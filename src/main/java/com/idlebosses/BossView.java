package com.idlebosses;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.FontManager;

// Draws the boss portrait into a box, for the HUD and the sidebar. Each animation frame is drawn once into an
// image, then that image is reused until the next frame, so a box redrawn every client frame stays cheap.
final class BossView
{
	// Where up to four hitsplats sit around the middle, as in the game.
	static final int[][] SPLAT_OFFSETS = {{0, 0}, {0, -22}, {-16, -11}, {16, -11}};
	private static final long SPLAT_MILLIS = 1200;
	private static final long FADE_IN_MILLIS = 400;
	private static final int PADDING = 6;

	private BufferedImage image;
	private BossFrame drawn;

	// Draws the boss and its hitsplats. Returns true while something is still moving (a hitsplat or a fade in).
	boolean paint(Graphics2D graphics, int x, int y, int width, int height, BossPortrait.Snapshot snapshot, SplatSprites splatSprites)
	{
		if (snapshot == null || snapshot.getFrame() == null || width <= 0 || height <= 0)
		{
			return false;
		}

		if (image == null || image.getWidth() != width || image.getHeight() != height)
		{
			image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			drawn = null;
		}
		if (snapshot.getFrame() != drawn)
		{
			render(snapshot);
			drawn = snapshot.getFrame();
		}

		long now = System.currentTimeMillis();
		long faded = now - snapshot.getFadeInMillis();
		Composite composite = graphics.getComposite();
		if (faded < FADE_IN_MILLIS)
		{
			graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0, (float) faded / FADE_IN_MILLIS)));
		}
		graphics.drawImage(image, x, y, null);
		graphics.setComposite(composite);

		boolean moving = faded < FADE_IN_MILLIS;
		int slot = 0;
		for (BossPortrait.Splat splat : snapshot.getSplats())
		{
			if (now - splat.getMillis() <= SPLAT_MILLIS && slot < SPLAT_OFFSETS.length)
			{
				drawSplat(graphics, splatSprites.get(splat.getType()), splat.getDamage(), x + width / 2 + SPLAT_OFFSETS[slot][0],
					y + height / 2 + SPLAT_OFFSETS[slot][1]);
				slot++;
				moving = true;
			}
		}
		return moving;
	}

	private void render(BossPortrait.Snapshot snapshot)
	{
		BossFrame frame = snapshot.getFrame();
		float[] bounds = snapshot.getBounds();
		int width = image.getWidth();
		int height = image.getHeight();
		float boundsWidth = Math.max(1, bounds[1] - bounds[0]);
		float boundsHeight = Math.max(1, bounds[3] - bounds[2]);
		double scale = Math.min((width - PADDING * 2) / boundsWidth, (height - PADDING * 2) / boundsHeight);
		// Centred across, standing on the bottom of the box.
		double offsetX = width / 2.0 - (bounds[0] + bounds[1]) / 2 * scale;
		double offsetY = height - PADDING - bounds[3] * scale;

		Graphics2D g = image.createGraphics();
		g.setComposite(AlphaComposite.Clear);
		g.fillRect(0, 0, width, height);
		g.setComposite(AlphaComposite.SrcOver);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(0.6f));
		Path2D.Float triangle = new Path2D.Float();
		for (int t = 0; t < frame.triangles; t++)
		{
			triangle.reset();
			triangle.moveTo(offsetX + frame.x[t * 3] * scale, offsetY + frame.y[t * 3] * scale);
			triangle.lineTo(offsetX + frame.x[t * 3 + 1] * scale, offsetY + frame.y[t * 3 + 1] * scale);
			triangle.lineTo(offsetX + frame.x[t * 3 + 2] * scale, offsetY + frame.y[t * 3 + 2] * scale);
			triangle.closePath();
			g.setColor(new Color(frame.rgb[t]));
			g.fill(triangle);
			// Drawing the edge too hides the hairline gaps between triangles.
			g.draw(triangle);
		}
		g.dispose();
	}

	static void drawSplat(Graphics2D graphics, BufferedImage sprite, int damage, int centreX, int centreY)
	{
		if (sprite == null)
		{
			return;
		}
		graphics.drawImage(sprite, centreX - sprite.getWidth() / 2, centreY - sprite.getHeight() / 2, null);
		// Put the font back after, or everything the HUD draws below the boss comes out in the small font.
		Font font = graphics.getFont();
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		String text = String.valueOf(damage);
		int textX = centreX - metrics.stringWidth(text) / 2;
		int textY = centreY + metrics.getAscent() / 2 - 1;
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, textX + 1, textY + 1);
		graphics.setColor(Color.WHITE);
		graphics.drawString(text, textX, textY);
		graphics.setFont(font);
	}
}
