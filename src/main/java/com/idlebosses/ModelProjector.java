package com.idlebosses;

import java.util.Arrays;
import java.util.function.IntUnaryOperator;
import net.runelite.api.JagexColor;
import net.runelite.api.Model;

// Flattens a posed boss model into triangles seen from a fixed three-quarter angle, using the colours the game lit
// it with. The angle, which faces count as facing away, and the depth order were checked by rendering the bosses
// from the game cache.
final class ModelProjector
{
	private static final double YAW = 0.6;
	private static final double PITCH = 0.3;
	// A face's third colour is -1 when it's one flat colour, and -2 when it isn't drawn.
	private static final int FLAT = -1;
	private static final int HIDDEN = -2;
	private static final int OPAQUE_LIMIT = 254;
	// The game's default brightness.
	private static final double BRIGHTNESS = 0.7;
	private static final int[] PALETTE = palette();

	private ModelProjector()
	{
	}

	// textureColor gives a texture's own colour, which textured faces are drawn in.
	static BossFrame project(Model model, IntUnaryOperator textureColor)
	{
		return project(model.getVerticesCount(), model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(),
			model.getFaceCount(), model.getFaceIndices1(), model.getFaceIndices2(), model.getFaceIndices3(),
			model.getFaceColors1(), model.getFaceColors2(), model.getFaceColors3(), model.getFaceTransparencies(),
			model.getFaceTextures(), textureColor);
	}

	// Colours are the game's lit HSL values, one per corner. A textured face's colours are only its lighting.
	static BossFrame project(int vertices, float[] vx, float[] vy, float[] vz, int faces, int[] a, int[] b, int[] c,
		int[] colors1, int[] colors2, int[] colors3, byte[] transparency, short[] textures, IntUnaryOperator textureColor)
	{
		double cosYaw = Math.cos(YAW);
		double sinYaw = Math.sin(YAW);
		double cosPitch = Math.cos(PITCH);
		double sinPitch = Math.sin(PITCH);

		float[] sx = new float[vertices];
		float[] sy = new float[vertices];
		float[] depth = new float[vertices];
		for (int v = 0; v < vertices; v++)
		{
			double x = vx[v] * cosYaw - vz[v] * sinYaw;
			double z = vx[v] * sinYaw + vz[v] * cosYaw;
			sx[v] = (float) x;
			sy[v] = (float) (vy[v] * cosPitch - z * sinPitch);
			depth[v] = (float) (vy[v] * sinPitch + z * cosPitch);
		}

		// Sort by depth with the face number packed underneath, furthest last.
		long[] order = new long[faces];
		int shown = 0;
		for (int f = 0; f < faces; f++)
		{
			if (colors3[f] == HIDDEN || transparency != null && (transparency[f] & 0xFF) >= OPAQUE_LIMIT)
			{
				continue;
			}
			int i = a[f];
			int j = b[f];
			int k = c[f];
			float facing = (sx[i] - sx[j]) * (sy[k] - sy[j]) - (sy[i] - sy[j]) * (sx[k] - sx[j]);
			if (facing <= 0)
			{
				continue;
			}
			long key = Math.round((depth[i] + depth[j] + depth[k]) * 4) + (1L << 30);
			order[shown++] = key << 32 | f;
		}
		Arrays.sort(order, 0, shown);

		float[] x = new float[shown * 3];
		float[] y = new float[shown * 3];
		int[] rgb = new int[shown];
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float minY = Float.MAX_VALUE;
		float maxY = -Float.MAX_VALUE;
		for (int n = 0; n < shown; n++)
		{
			int f = (int) order[shown - 1 - n];
			int[] corners = {a[f], b[f], c[f]};
			for (int corner = 0; corner < 3; corner++)
			{
				int v = corners[corner];
				x[n * 3 + corner] = sx[v];
				y[n * 3 + corner] = sy[v];
				minX = Math.min(minX, sx[v]);
				maxX = Math.max(maxX, sx[v]);
				minY = Math.min(minY, sy[v]);
				maxY = Math.max(maxY, sy[v]);
			}
			int color = faceColor(colors1[f], colors2[f], colors3[f]);
			if (textures != null && textures[f] >= 0 && textureColor != null)
			{
				// The texture's colour, at this face's lighting, as the game draws it with textures off.
				color = hsl(textureColor.applyAsInt(textures[f])) & 0xFF80 | color & 0x7F;
			}
			rgb[n] = PALETTE[color];
		}
		return new BossFrame(x, y, rgb, shown, minX, maxX, minY, maxY);
	}

	// A smooth-shaded face has a lit colour at each corner; one flat colour is close enough at this size.
	private static int faceColor(int first, int second, int third)
	{
		if (third == FLAT)
		{
			return first & 0xFFFF;
		}
		int lightness = ((first & 0x7F) + (second & 0x7F) + (third & 0x7F)) / 3;
		return (first & 0xFF80 | lightness) & 0xFFFF;
	}

	// The texture colour should already be the game's HSL; an RGB one is converted.
	private static int hsl(int color)
	{
		return color > 0xFFFF ? JagexColor.rgbToHSL(color, BRIGHTNESS) & 0xFFFF : color;
	}

	// The game's colours are 6 bits of hue, 3 of saturation and 7 of lightness. This is how it turns them into RGB.
	private static int[] palette()
	{
		int[] palette = new int[1 << 16];
		for (int hsl = 0; hsl < palette.length; hsl++)
		{
			double hue = (hsl >> 10 & 0x3F) / 64.0 + 0.0078125;
			double saturation = (hsl >> 7 & 0x7) / 8.0 + 0.0625;
			double lightness = (hsl & 0x7F) / 128.0;
			double q = lightness < 0.5 ? lightness * (1 + saturation) : lightness + saturation - lightness * saturation;
			double p = 2 * lightness - q;
			int r = channel(p, q, hue + 1 / 3.0);
			int g = channel(p, q, hue);
			int bl = channel(p, q, hue - 1 / 3.0);
			palette[hsl] = r << 16 | g << 8 | bl;
		}
		return palette;
	}

	private static int channel(double p, double q, double t)
	{
		if (t < 0)
		{
			t += 1;
		}
		if (t > 1)
		{
			t -= 1;
		}
		double value;
		if (6 * t < 1)
		{
			value = p + (q - p) * 6 * t;
		}
		else if (2 * t < 1)
		{
			value = q;
		}
		else if (3 * t < 2)
		{
			value = p + (q - p) * (2 / 3.0 - t) * 6;
		}
		else
		{
			value = p;
		}
		return Math.min(255, (int) (Math.pow(value, BRIGHTNESS) * 256));
	}
}
