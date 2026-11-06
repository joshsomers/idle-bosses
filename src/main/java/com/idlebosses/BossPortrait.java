package com.idlebosses;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Value;
import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.ModelData;

// The animated boss shown in the HUD and the sidebar: it idles, flinches with each hit, dies and respawns, using
// the boss's own animations. Runs on the client thread and publishes each frame as a Snapshot for the views.
@Singleton
class BossPortrait
{
	private static final int RESPAWN_DELAY_CYCLES = 30;
	private static final int MAX_SPLATS = BossView.SPLAT_OFFSETS.length;
	// Room around the idle pose for attacks and deaths that reach further.
	private static final float BOUNDS_MARGIN = 0.12f;

	private final Client client;

	private Boss boss;
	private MiniBoss.Variant variant = MiniBoss.Variant.NORMAL;
	private Model base;
	private AnimationController controller;
	private float[] bounds;
	private boolean dying;
	private boolean spawning;
	private int hideAtCycle = -1;
	private int spawnAtCycle = -1;
	private int lastCycle;
	private int lastFrame = -1;
	private boolean dirty;
	private BossFrame frame;
	private long fadeInMillis;
	private List<Splat> splats = List.of();

	@Getter
	private volatile Snapshot snapshot;

	@Inject
	BossPortrait(Client client)
	{
		this.client = client;
	}

	void set(Boss boss, MiniBoss.Variant variant)
	{
		if (boss == this.boss && variant == this.variant && snapshot != null && !dying)
		{
			return;
		}
		this.boss = boss;
		this.variant = variant;
		dying = false;
		hideAtCycle = -1;
		spawnAtCycle = -1;
		splats = List.of();
		spawn();
	}

	void clear()
	{
		boss = null;
		base = null;
		controller = null;
		frame = null;
		dying = false;
		spawning = false;
		hideAtCycle = -1;
		spawnAtCycle = -1;
		splats = List.of();
		snapshot = null;
	}

	// Splats are kept while one boss dies and the next appears, so a hit in between still shows on the new one.
	void hit(int damage, Hit.Type type)
	{
		if (boss == null)
		{
			return;
		}
		List<Splat> next = new ArrayList<>(splats);
		next.add(new Splat(damage, type, System.currentTimeMillis()));
		while (next.size() > MAX_SPLATS)
		{
			next.remove(0);
		}
		splats = List.copyOf(next);
		publish();

		if (base != null && !dying && !spawning && boss.getDefendAnimation() != -1)
		{
			playOnce(client.loadAnimation(boss.getDefendAnimation()), this::idle);
		}
	}

	// The boss died: play its death, then bring in the next one.
	void kill(Boss next, MiniBoss.Variant nextVariant)
	{
		Boss dead = boss;
		boss = next;
		variant = nextVariant;
		if (base == null || dying || dead == null)
		{
			if (!dying)
			{
				spawn();
			}
			return;
		}

		dying = true;
		spawning = false;
		Animation death = client.loadAnimation(dead.getDeathAnimation());
		hideAtCycle = client.getGameCycle() + BossModels.deathCycles(death);
		playOnce(death, this::finishDeath);
	}

	void clientTick()
	{
		int cycle = client.getGameCycle();
		int cycles = Math.max(1, Math.min(10, cycle - lastCycle));
		lastCycle = cycle;

		if (dying && cycle >= hideAtCycle)
		{
			finishDeath();
		}
		if (spawnAtCycle >= 0 && cycle >= spawnAtCycle)
		{
			spawnAtCycle = -1;
			spawn();
		}
		if (base == null)
		{
			return;
		}

		if (controller != null)
		{
			controller.tick(cycles);
		}
		// Finishing a death inside the tick takes the boss away.
		if (base == null)
		{
			return;
		}
		int at = controller == null ? -1 : controller.getFrame();
		if (dirty || at != lastFrame)
		{
			lastFrame = at;
			dirty = false;
			frame = ModelProjector.project(posed(), this::textureColor);
			publish();
		}
	}

	private void spawn()
	{
		base = null;
		controller = null;
		frame = null;
		snapshot = null;
		if (boss == null)
		{
			return;
		}
		ModelData data = BossModels.load(client, boss, variant, 0);
		if (data == null)
		{
			return;
		}
		base = data.light();

		// Fit the box to the first frame of the idle pose, so the zoom doesn't change as the boss moves.
		idle();
		BossFrame idle = ModelProjector.project(posed(), this::textureColor);
		float marginX = (idle.maxX - idle.minX) * BOUNDS_MARGIN;
		float marginY = (idle.maxY - idle.minY) * BOUNDS_MARGIN;
		bounds = new float[]{idle.minX - marginX, idle.maxX + marginX, idle.minY - marginY, idle.maxY};

		if (boss.getSpawnAnimation() != -1)
		{
			spawning = true;
			fadeInMillis = 0;
			playOnce(client.loadAnimation(boss.getSpawnAnimation()), this::idle);
		}
		else
		{
			fadeInMillis = System.currentTimeMillis();
		}
		dirty = true;
	}

	private void finishDeath()
	{
		if (!dying)
		{
			return;
		}
		dying = false;
		hideAtCycle = -1;
		base = null;
		controller = null;
		frame = null;
		snapshot = null;
		spawnAtCycle = client.getGameCycle() + RESPAWN_DELAY_CYCLES;
	}

	private void idle()
	{
		spawning = false;
		if (boss == null || dying)
		{
			return;
		}
		Animation idle = client.loadAnimation(boss.getIdleAnimation());
		controller = idle == null ? null : new AnimationController(client, idle);
		if (controller != null)
		{
			controller.loop();
		}
		dirty = true;
	}

	private void playOnce(Animation animation, Runnable after)
	{
		if (animation == null)
		{
			after.run();
			return;
		}
		controller = new AnimationController(client, animation);
		controller.setOnFinished(c -> after.run());
		dirty = true;
	}

	private int textureColor(int texture)
	{
		return client.getTextureProvider() == null ? 0 : client.getTextureProvider().getDefaultColor(texture);
	}

	private Model posed()
	{
		return controller == null || controller.getAnimation() == null ? base : controller.animate(base);
	}

	private void publish()
	{
		snapshot = frame == null ? null : new Snapshot(frame, bounds, fadeInMillis, splats);
	}

	@Value
	static class Snapshot
	{
		BossFrame frame;
		// Min x, max x, min y, max y of the area to fit in the box, in model units.
		float[] bounds;
		long fadeInMillis;
		List<Splat> splats;
	}

	@Value
	static class Splat
	{
		int damage;
		Hit.Type type;
		long millis;
	}
}
