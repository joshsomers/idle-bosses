package com.idlebosses;

import net.runelite.api.Animation;
import net.runelite.api.Client;
import net.runelite.api.ModelData;
import net.runelite.api.NPCComposition;
import net.runelite.api.Perspective;

// Loads boss models and works out animation timings, for both the walking boss and the portrait.
// Only used on the client thread.
final class BossModels
{
	// A death animation's last frame is often held for many seconds, so it's cut this many client cycles (20ms) in.
	private static final int DEATH_HOLD_CYCLES = 30;
	private static final int MAX_DEATH_CYCLES = 200;
	private static final int MODEL_SCALE_UNIT = 128;
	// Colour values are 6 bits of hue, 3 of saturation and 7 of lightness.
	private static final int SUPERIOR_HUE = 51;
	private static final int SUPERIOR_SATURATION = 5;
	private static final int GOLD_HUE = 8;
	private static final int GOLD_SATURATION = 6;
	private static final int GOLD_LIGHTEN = 12;

	private BossModels()
	{
	}

	// The boss's model, copied so the game's own cached models aren't changed. With tiles above 0 it's scaled to
	// that many tiles wide; otherwise it keeps its own size.
	static ModelData load(Client client, Boss boss, MiniBoss.Variant variant, double tiles)
	{
		ModelData data = boss.getModelId() >= 0 ? loadScenery(client, boss.getModelId()) : loadNpc(client, boss.getNpcId());
		if (data == null)
		{
			return null;
		}

		// Sized by how wide the model really is: some NPCs claim one tile while their model spans five.
		if (tiles > 0)
		{
			int scale = (int) Math.round(MODEL_SCALE_UNIT * tiles * Perspective.LOCAL_TILE_SIZE / Math.max(1, width(data)));
			data.scale(scale, scale, scale);
		}

		if (variant != MiniBoss.Variant.NORMAL)
		{
			data = data.cloneColors();
			short[] colors = data.getFaceColors();
			for (int i = 0; i < colors.length; i++)
			{
				colors[i] = tint(colors[i], variant);
			}
		}
		return data;
	}

	// The NPC's models at its own size and colours, copied so the game's cached models aren't changed.
	private static ModelData loadNpc(Client client, int npcId)
	{
		NPCComposition npc = client.getNpcDefinition(npcId);
		if (npc == null || npc.getModels() == null || npc.getModels().length == 0)
		{
			return null;
		}

		int[] ids = npc.getModels();
		ModelData[] parts = new ModelData[ids.length];
		for (int i = 0; i < ids.length; i++)
		{
			parts[i] = client.loadModelData(ids[i]);
			if (parts[i] == null)
			{
				return null;
			}
		}

		ModelData data = (parts.length == 1 ? parts[0] : client.mergeModels(parts)).shallowCopy().cloneVertices();
		data.scale(npc.getWidthScale(), npc.getHeightScale(), npc.getWidthScale());

		// Some bosses are another model in different colours, like the three Dagannoth Kings.
		short[] find = npc.getColorToReplace();
		short[] replace = npc.getColorToReplaceWith();
		if (find != null && replace != null)
		{
			data = data.cloneColors();
			for (int i = 0; i < Math.min(find.length, replace.length); i++)
			{
				data.recolor(find[i], replace[i]);
			}
		}
		return data;
	}

	private static ModelData loadScenery(Client client, int modelId)
	{
		ModelData data = client.loadModelData(modelId);
		return data == null ? null : data.shallowCopy().cloneVertices();
	}

	// The model's widest span across the ground, in local units.
	private static float width(ModelData data)
	{
		float[] xs = data.getVerticesX();
		float[] zs = data.getVerticesZ();
		float minX = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE;
		float minZ = Float.MAX_VALUE;
		float maxZ = -Float.MAX_VALUE;
		for (int i = 0; i < data.getVerticesCount(); i++)
		{
			minX = Math.min(minX, xs[i]);
			maxX = Math.max(maxX, xs[i]);
			minZ = Math.min(minZ, zs[i]);
			maxZ = Math.max(maxZ, zs[i]);
		}
		return Math.max(maxX - minX, maxZ - minZ);
	}

	static short tint(short color, MiniBoss.Variant variant)
	{
		int lightness = color & 0x7F;
		if (variant == MiniBoss.Variant.GOLD)
		{
			return (short) (GOLD_HUE << 10 | GOLD_SATURATION << 7 | Math.min(0x7F, lightness + GOLD_LIGHTEN));
		}
		return (short) (SUPERIOR_HUE << 10 | SUPERIOR_SATURATION << 7 | lightness);
	}

	// The game doesn't measure a model's height until it has drawn it, so measure it from the vertices.
	// Model Y points down from the feet, so the top is the most negative Y; the other side covers a flipped model.
	static int height(float[] verticesY)
	{
		float min = 0;
		float max = 0;
		for (float vertexY : verticesY)
		{
			min = Math.min(min, vertexY);
			max = Math.max(max, vertexY);
		}
		return Math.round(Math.max(-min, max));
	}

	// How long to show a death animation before taking the boss away, in client cycles.
	static int deathCycles(Animation animation)
	{
		if (animation == null)
		{
			return 0;
		}
		if (animation.isMayaAnim())
		{
			return Math.min(animation.getDuration(), MAX_DEATH_CYCLES);
		}
		int[] lengths = animation.getFrameLengths();
		if (lengths == null || lengths.length == 0)
		{
			return 0;
		}
		int total = 0;
		for (int i = 0; i < lengths.length - 1; i++)
		{
			total += lengths[i];
		}
		return Math.min(total + Math.min(lengths[lengths.length - 1], DEATH_HOLD_CYCLES), MAX_DEATH_CYCLES);
	}
}
