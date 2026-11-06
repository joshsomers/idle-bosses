package com.idlebosses;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Animation;
import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Model;
import net.runelite.api.ModelData;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.RuneLiteObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.gameval.AnimationID;

// The small boss next to the player. It's a client-side model: it can't be clicked and nobody else sees it.
// It walks after the player along the tiles they've walked, so it doesn't cut through walls, and only jumps
// to them when they teleport or change floor. Only used on the client thread.
@Singleton
class MiniBoss
{
	enum Variant
	{
		NORMAL,
		SUPERIOR,
		// The boss's collection log is complete.
		GOLD
	}

	// The model of the game's smoke puff (SpotanimID.SMOKEPUFF). There are no gameval constants for models.
	private static final int SMOKE_PUFF_MODEL = 3076;
	private static final int MODEL_SCALE_UNIT = 128;
	private static final int RESPAWN_DELAY_CYCLES = 30;
	// Walking pace is a tile a game tick, which is 30 client cycles. It speeds up to catch a running player.
	private static final double WALK_SPEED = (double) Perspective.LOCAL_TILE_SIZE / 30;
	private static final double MAX_SPEED = WALK_SPEED * 3;
	private static final double CATCH_UP_CYCLES = 30;
	// Turning speed in 2048ths of a turn a client cycle, close to an NPC's.
	private static final int TURN_SPEED = 32;
	// The player moving further than this in one tick is a teleport, not a walk or a run.
	private static final int TELEPORT_TILES = 3;
	private static final int MAX_DISTANCE_TILES = 10;
	private static final int MAX_TRAIL = 32;
	private static final int BLOCKED = CollisionDataFlag.BLOCK_MOVEMENT_FULL
		| CollisionDataFlag.BLOCK_MOVEMENT_OBJECT
		| CollisionDataFlag.BLOCK_MOVEMENT_FLOOR;
	// Two tiles out so the boss doesn't stand inside the player, then closer if those are blocked.
	private static final int[][] SPAWN_OFFSETS = {
		{2, 0}, {0, -2}, {-2, 0}, {0, 2}, {2, -1}, {2, 1}, {-2, -1}, {-2, 1},
		{1, -2}, {-1, -2}, {1, 2}, {-1, 2}, {1, 0}, {0, -1}, {-1, 0}, {0, 1},
	};

	private final Client client;

	private RuneLiteObject object;
	private RuneLiteObject puff;

	private Boss boss;
	private Variant variant = Variant.NORMAL;
	private MiniBossSize size = MiniBossSize.SMALL;
	private boolean enabled;

	private boolean dying;
	private boolean spawning;
	private boolean walking;
	private int hideAtCycle = -1;
	private int spawnAtCycle = -1;
	private int lastCycle;
	// The boss height above its tile, for drawing the health bar and hitsplats.
	@Getter
	private int modelHeight;

	// Exact position, so slow movement isn't lost to rounding.
	private double x;
	private double y;
	private int orientation;
	// The tiles the player has walked, oldest first. The boss walks to the one a few steps behind them.
	private final Deque<LocalPoint> trail = new ArrayDeque<>();
	private LocalPoint target;

	@Inject
	MiniBoss(Client client)
	{
		this.client = client;
	}

	// Shows this boss, replacing any other. Pass enabled false to keep it hidden.
	void set(Boss boss, Variant variant, MiniBossSize size, boolean enabled)
	{
		boolean changed = boss != this.boss || variant != this.variant || size != this.size;
		this.boss = boss;
		this.variant = variant;
		this.size = size;
		this.enabled = enabled;
		if (!enabled || boss == null)
		{
			despawn();
		}
		else if (changed || !isVisible())
		{
			despawn();
			spawn();
		}
	}

	// The boss died: play its death, then bring in the next one.
	void kill(Boss next, Variant nextVariant)
	{
		Boss dead = boss;
		boss = next;
		variant = nextVariant;
		// Hidden: the next boss appears on the next tick. Already dying: the respawn shows the new one.
		if (!isVisible() || dying || dead == null)
		{
			return;
		}

		dying = true;
		spawning = false;
		walking = false;
		target = null;
		Animation death = client.loadAnimation(dead.getDeathAnimation());
		hideAtCycle = client.getGameCycle() + BossModels.deathCycles(death);
		playOnce(death, this::finishDeath);
	}

	void hit()
	{
		// A flinch would cut the spawn animation short, and freeze the legs mid-walk.
		if (isAlive() && !spawning && !walking && boss.getDefendAnimation() != -1)
		{
			playOnce(client.loadAnimation(boss.getDefendAnimation()), this::pose);
		}
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
		if (isAlive())
		{
			move(cycles);
		}
	}

	void gameTick()
	{
		Player player = client.getLocalPlayer();
		if (!enabled || boss == null || player == null)
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		LocalPoint here = player.getLocalLocation();
		if (!isVisible())
		{
			if (!dying && spawnAtCycle < 0)
			{
				spawn();
			}
			return;
		}
		if (dying || here == null)
		{
			return;
		}

		LocalPoint last = trail.peekLast();
		boolean teleported = last != null && tiles(last, here) > TELEPORT_TILES;
		if (player.getWorldView() != worldView || object.getLevel() != worldView.getPlane() || teleported
			|| tiles(object.getLocation(), here) > MAX_DISTANCE_TILES)
		{
			// Teleports, ladders and boats: take the boss away and bring it in next to the player.
			despawn();
			spawn();
			return;
		}

		if (last == null || last.getSceneX() != here.getSceneX() || last.getSceneY() != here.getSceneY())
		{
			trail.addLast(here);
			while (trail.size() > MAX_TRAIL)
			{
				trail.removeFirst();
			}
		}

		// The tile the player stood on a few steps ago, so the boss trails behind without crowding them.
		int behind = followSteps();
		if (trail.size() > behind)
		{
			Iterator<LocalPoint> it = trail.descendingIterator();
			for (int i = 0; i < behind; i++)
			{
				it.next();
			}
			target = it.next();
		}
	}

	// Takes the models out of the scene. They come back on the next tick.
	void despawn()
	{
		dying = false;
		spawning = false;
		walking = false;
		hideAtCycle = -1;
		spawnAtCycle = -1;
		target = null;
		trail.clear();
		if (object != null)
		{
			object.setActive(false);
		}
		if (puff != null)
		{
			puff.setActive(false);
		}
	}

	void shutDown()
	{
		despawn();
		enabled = false;
		boss = null;
		object = null;
		puff = null;
	}

	boolean isAlive()
	{
		return isVisible() && !dying;
	}

	LocalPoint getLocation()
	{
		return isAlive() ? object.getLocation() : null;
	}

	int getLevel()
	{
		return object == null ? 0 : object.getLevel();
	}

	private boolean isVisible()
	{
		return object != null && object.isActive();
	}

	private int followSteps()
	{
		return 1 + (int) Math.ceil(size.getTiles());
	}

	private void move(int cycles)
	{
		Player player = client.getLocalPlayer();
		LocalPoint here = player == null ? null : player.getLocalLocation();
		double dx = target == null ? 0 : target.getX() - x;
		double dy = target == null ? 0 : target.getY() - y;
		double distance = Math.hypot(dx, dy);

		if (distance < 1)
		{
			if (walking)
			{
				walking = false;
				pose();
			}
			// Standing still, so look at the player.
			if (here != null)
			{
				turnTowards(here.getX() - x, here.getY() - y, cycles);
			}
			return;
		}

		double speed = Math.max(WALK_SPEED, Math.min(MAX_SPEED, distance / CATCH_UP_CYCLES));
		double step = Math.min(distance, speed * cycles);
		x += dx / distance * step;
		y += dy / distance * step;
		object.setLocation(new LocalPoint((int) Math.round(x), (int) Math.round(y), client.getTopLevelWorldView()), object.getLevel());
		turnTowards(dx, dy, cycles);
		if (!walking)
		{
			walking = true;
			pose();
		}
	}

	private void turnTowards(double dx, double dy, int cycles)
	{
		if (Math.abs(dx) < 1 && Math.abs(dy) < 1)
		{
			return;
		}
		// Orientation 0 faces south and 512 west, in 2048ths of a turn.
		int wanted = (int) Math.round(Math.atan2(-dx, -dy) * 1024 / Math.PI) & 2047;
		int difference = ((wanted - orientation + 3072) & 2047) - 1024;
		int turn = Math.max(-TURN_SPEED * cycles, Math.min(TURN_SPEED * cycles, difference));
		orientation = (orientation + turn) & 2047;
		object.setOrientation(orientation);
	}

	private void spawn()
	{
		Player player = client.getLocalPlayer();
		if (!enabled || boss == null || player == null || player.getWorldView() != client.getTopLevelWorldView())
		{
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		LocalPoint here = player.getLocalLocation();
		LocalPoint tile = pickTile(worldView, here);
		ModelData data = BossModels.load(client, boss, variant, size.getTiles());
		if (tile == null || data == null)
		{
			return;
		}

		Model model = data.light();
		modelHeight = BossModels.height(data.getVerticesY());
		if (object == null)
		{
			object = client.createRuneLiteObject();
		}
		object.setModel(model);
		object.setLocation(tile, worldView.getPlane());
		x = tile.getX();
		y = tile.getY();
		orientation = (int) Math.round(Math.atan2(-(here.getX() - x), -(here.getY() - y)) * 1024 / Math.PI) & 2047;
		object.setOrientation(orientation);
		trail.clear();
		trail.addLast(here);
		target = null;
		walking = false;
		object.setActive(true);

		if (boss.getSpawnAnimation() != -1)
		{
			spawning = true;
			playOnce(client.loadAnimation(boss.getSpawnAnimation()), this::pose);
		}
		else
		{
			pose();
			puff(tile, worldView.getPlane());
		}
	}

	private void finishDeath()
	{
		if (!dying)
		{
			return;
		}
		dying = false;
		hideAtCycle = -1;
		if (object != null)
		{
			object.setActive(false);
		}
		spawnAtCycle = client.getGameCycle() + RESPAWN_DELAY_CYCLES;
	}

	// Loops the walk or idle animation, whichever fits.
	private void pose()
	{
		spawning = false;
		if (object == null || boss == null || dying)
		{
			return;
		}
		Animation animation = client.loadAnimation(walking ? boss.getWalkAnimation() : boss.getIdleAnimation());
		object.setShouldLoop(true);
		object.setAnimation(animation);
	}

	private void playOnce(Animation animation, Runnable after)
	{
		if (object == null)
		{
			return;
		}
		if (animation == null)
		{
			after.run();
			return;
		}
		object.setShouldLoop(false);
		AnimationController controller = new AnimationController(client, animation);
		object.setAnimationController(controller);
		// Set after the controller, which replaces any finish handler with its own.
		controller.setOnFinished(c -> after.run());
	}

	private void puff(LocalPoint tile, int plane)
	{
		ModelData data = client.loadModelData(SMOKE_PUFF_MODEL);
		Animation animation = client.loadAnimation(AnimationID.SMOKEPUFF);
		if (data == null || animation == null)
		{
			return;
		}

		int scale = (int) Math.round(MODEL_SCALE_UNIT * size.getTiles());
		if (puff == null)
		{
			puff = client.createRuneLiteObject();
		}
		RuneLiteObject smoke = puff;
		smoke.setModel(data.shallowCopy().cloneVertices().scale(scale, scale, scale).light());
		smoke.setLocation(tile, plane);
		smoke.setShouldLoop(false);
		smoke.setAnimation(animation);
		smoke.getAnimationController().setOnFinished(c -> smoke.setActive(false));
		smoke.setActive(true);
	}

	private static LocalPoint pickTile(WorldView worldView, LocalPoint player)
	{
		if (player == null)
		{
			return null;
		}
		CollisionData[] maps = worldView.getCollisionMaps();
		int plane = worldView.getPlane();
		int[][] flags = maps != null && maps[plane] != null ? maps[plane].getFlags() : null;
		for (int[] offset : SPAWN_OFFSETS)
		{
			int tileX = player.getSceneX() + offset[0];
			int tileY = player.getSceneY() + offset[1];
			if (tileX < 1 || tileY < 1 || tileX >= worldView.getSizeX() - 1 || tileY >= worldView.getSizeY() - 1)
			{
				continue;
			}
			if (flags != null && (flags[tileX][tileY] & BLOCKED) != 0)
			{
				continue;
			}
			return LocalPoint.fromScene(tileX, tileY, worldView);
		}
		return null;
	}

	private static int tiles(LocalPoint a, LocalPoint b)
	{
		if (a == null || b == null)
		{
			return Integer.MAX_VALUE;
		}
		return Math.max(Math.abs(a.getSceneX() - b.getSceneX()), Math.abs(a.getSceneY() - b.getSceneY()));
	}
}
