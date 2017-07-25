package openblocks.common;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import info.openmods.calc.Environment;
import info.openmods.calc.ExprType;
import info.openmods.calc.SingleExprEvaluator;
import info.openmods.calc.SingleExprEvaluator.EnvironmentConfigurator;
import info.openmods.calc.types.fp.DoubleCalculatorFactory;
import java.util.Map;
import java.util.Random;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import openblocks.Config;
import openblocks.OpenBlocks;
import openblocks.common.item.ItemTrophyBlock;
import openblocks.common.tileentity.TileEntityTrophy;
import openblocks.trophy.BlazeBehavior;
import openblocks.trophy.CaveSpiderBehavior;
import openblocks.trophy.CreeperBehavior;
import openblocks.trophy.EndermanBehavior;
import openblocks.trophy.EvocationBehaviour;
import openblocks.trophy.GuardianBehavior;
import openblocks.trophy.ITrophyBehavior;
import openblocks.trophy.ItemDropBehavior;
import openblocks.trophy.MooshroomBehavior;
import openblocks.trophy.ShulkerBehavior;
import openblocks.trophy.SkeletonBehavior;
import openblocks.trophy.SnowmanBehavior;
import openblocks.trophy.SquidBehavior;
import openblocks.trophy.WitchBehavior;
import openmods.Log;
import openmods.config.properties.ConfigurationChange;
import openmods.reflection.ReflectionHelper;

public class TrophyHandler {

	private static final Map<Trophy, Entity> ENTITY_CACHE = Maps.newHashMap();

	private final Random fallbackDropChance = new Random();

	private final SingleExprEvaluator<Double, ExprType> dropChanceCalculator = SingleExprEvaluator.create(DoubleCalculatorFactory.createDefault());

	{
		updateDropChanceFormula();
	}

	@SubscribeEvent
	public void onConfigChange(ConfigurationChange.Post evt) {
		if (evt.check("trophy", "trophyDropChanceFormula"))
			updateDropChanceFormula();
	}

	private void updateDropChanceFormula() {
		dropChanceCalculator.setExpr(ExprType.INFIX, Config.trophyDropChanceFormula);

		if (!dropChanceCalculator.isExprValid())
			Log.info("Invalid trophyDropChanceFormula formula: ", Config.trophyDropChanceFormula);
	}

	public static Entity getEntityFromCache(Trophy trophy) {
		Entity entity = ENTITY_CACHE.get(trophy);
		if (entity == null) {
			if (!ENTITY_CACHE.containsKey(trophy)) {
				try {
					entity = trophy.createEntity();
				} catch (Throwable t) {
					Log.severe(t, "Failed to create dummy entity for trophy %s", trophy);
				}
			}
			ENTITY_CACHE.put(trophy, entity);
		}
		return entity;
	}

	private static Entity setSlimeSize(Entity entity, int size) {
		try {
			ReflectionHelper.call(entity, new String[] { "func_70799_a", "setSlimeSize" }, ReflectionHelper.primitive(size));
		} catch (Exception e) {
			Log.warn(e, "Can't update slime size");
		}
		return entity;
	}

	private static final ResourceLocation mc(String id) {
		return new ResourceLocation("minecraft", id);
	}

	public enum Trophy {
		Wolf(mc("wolf")),
		Chicken(mc("chicken"), new ItemDropBehavior(10000, new ItemStack(Items.EGG), SoundEvents.ENTITY_CHICKEN_EGG)),
		Cow(mc("cow"), new ItemDropBehavior(20000, new ItemStack(Items.LEATHER))),
		Creeper(mc("creeper"), new CreeperBehavior()),
		Skeleton(mc("skeleton"), new SkeletonBehavior()),
		PigZombie(mc("zombie_pigman"), new ItemDropBehavior(20000, new ItemStack(Items.GOLD_NUGGET))),
		Bat(mc("bat"), 1.0, -0.3),
		Zombie(mc("zombie")),
		Witch(mc("witch"), 0.35, new WitchBehavior()),
		Villager(mc("villager")),
		Ozelot(mc("ocelot")) {
			@Override
			protected Entity createEntity() {
				Entity entity = super.createEntity();

				try {
					((EntityOcelot)entity).setTamed(true);
				} catch (ClassCastException e) {
					Log.warn("Invalid cat entity class: %s", entity.getClass());
				}
				return entity;
			}
		},
		Sheep(mc("sheep")),
		Blaze(mc("blaze"), new BlazeBehavior()),
		Silverfish(mc("sliverfish")),
		Spider(mc("spider")),
		CaveSpider(mc("cave_spider"), new CaveSpiderBehavior()),
		Slime(mc("slime"), 0.6) {
			@Override
			protected Entity createEntity() {
				return setSlimeSize(super.createEntity(), 1);
			}
		},
		Ghast(mc("ghast"), 0.1, 0.3),
		Enderman(mc("enderman"), 0.3, new EndermanBehavior()),
		LavaSlime(mc("magma_cube"), 0.6) {
			@Override
			protected Entity createEntity() {
				return setSlimeSize(super.createEntity(), 1);
			}
		},
		Squid(mc("squid"), 0.3, 0.5, new SquidBehavior()),
		MushroomCow(mc("mooshroom"), new MooshroomBehavior()),
		VillagerGolem(mc("villager_golem"), 0.3),
		SnowMan(mc("snowman"), new SnowmanBehavior()),
		Pig(mc("pig"), new ItemDropBehavior(20000, new ItemStack(Items.PORKCHOP))),
		Endermite(mc("endermite")),
		Guardian(mc("guardian"), new GuardianBehavior()),
		Rabbit(mc("rabbit"), new ItemDropBehavior(20000, new ItemStack(Items.CARROT))),
		PolarBear(mc("polar_bear"), new ItemDropBehavior(20000, new ItemStack(Items.FISH))),
		Shulker(mc("shulker"), new ShulkerBehavior()),
		ElderGuardian(mc("elder_guardian"), 0.2, 0.3, new GuardianBehavior()),
		WitherSkeleton(mc("wither_skeleton"), new ItemDropBehavior(50000, new ItemStack(Items.SKULL, 1, 1))),
		Stray(mc("stray"), new SkeletonBehavior()),
		Husk(mc("husk"), new ItemDropBehavior(20000, new ItemStack(Items.FEATHER))),
		ZombieVillager(mc("zombie_villager")),
		EvocationIllager(mc("evocation_illager"), new EvocationBehaviour()),
		Vex(mc("vex")),
		VindicationIllager(mc("vindication_illager"), new ItemDropBehavior(20000, new ItemStack(Items.IRON_AXE)));
		// Skipped: Horse (needs non-null, world in ctor), Wither (renders boss bar)

		private double scale = 0.4;
		private double verticalOffset = 0.0;
		private ITrophyBehavior behavior;
		public final ResourceLocation id;

		private Trophy(ResourceLocation id) {
			this.id = id;
		}

		private Trophy(ResourceLocation id, ITrophyBehavior behavior) {
			this(id);
			this.behavior = behavior;
		}

		private Trophy(ResourceLocation id, double scale) {
			this(id);
			this.scale = scale;
		}

		private Trophy(ResourceLocation id, double scale, ITrophyBehavior behavior) {
			this(id);
			this.scale = scale;
			this.behavior = behavior;
		}

		private Trophy(ResourceLocation id, double scale, double verticalOffset) {
			this(id, scale);
			this.verticalOffset = verticalOffset;
		}

		private Trophy(ResourceLocation id, double scale, double verticalOffset, ITrophyBehavior behavior) {
			this(id, scale, verticalOffset);
			this.behavior = behavior;
		}

		public double getVerticalOffset() {
			return verticalOffset;
		}

		public double getScale() {
			return scale;
		}

		public Entity getEntity() {
			return getEntityFromCache(this);
		}

		public ItemStack getItemStack() {
			return ItemTrophyBlock.putMetadata(new ItemStack(OpenBlocks.Blocks.trophy), this);
		}

		public void playSound(World world, BlockPos pos) {
			if (world == null) return;

			Entity e = getEntity();
			if (e instanceof EntityLiving) {
				e.posX = pos.getX();
				e.posY = pos.getY();
				e.posZ = pos.getZ();

				synchronized (e) {
					e.world = world;
					((EntityLiving)e).playLivingSound();
					e.world = null;
				}
			}
		}

		public int executeActivateBehavior(TileEntityTrophy tile, EntityPlayer player) {
			if (behavior != null) return behavior.executeActivateBehavior(tile, player);
			return 0;
		}

		public void executeTickBehavior(TileEntityTrophy tile) {
			if (behavior != null) behavior.executeTickBehavior(tile);
		}

		protected Entity createEntity() {
			return EntityList.createEntityByIDFromName(id, null);
		}

		static {

			ImmutableMap.Builder<ResourceLocation, Trophy> byId = ImmutableMap.builder();
			ImmutableMap.Builder<String, Trophy> byName = ImmutableMap.builder();

			for (Trophy t : values()) {
				byId.put(t.id, t);
				byName.put(t.name(), t);
			}

			TYPES_BY_ID = byId.build();
			TYPES_BY_NAME = byName.build();
		}

		public final static Map<String, Trophy> TYPES_BY_NAME;

		public final static Map<ResourceLocation, Trophy> TYPES_BY_ID;

		public final static Trophy[] VALUES = values();
	}

	@SubscribeEvent
	public void onLivingDrops(final LivingDropsEvent event) {
		final Entity entity = event.getEntity();
		if (event.isRecentlyHit() && canDrop(entity)) {
			final Double result = dropChanceCalculator.evaluate(
					new EnvironmentConfigurator<Double>() {
						@Override
						public void accept(Environment<Double> env) {
							env.setGlobalSymbol("looting", Double.valueOf(event.getLootingLevel()));
							env.setGlobalSymbol("chance", Config.trophyDropChance);
						}
					}, new Supplier<Double>() {
						@Override
						public Double get() {
							final double bias = fallbackDropChance.nextDouble() / 4;
							final double selection = fallbackDropChance.nextDouble();
							return (event.getLootingLevel() + bias) * Config.trophyDropChance - selection;
						}
					});

			if (result > 0) {
				final ResourceLocation entityName = EntityList.getKey(entity.getClass());
				if (entityName != null) {
					Trophy mobTrophy = Trophy.TYPES_BY_ID.get(entityName);
					if (mobTrophy != null) {
						EntityItem drop = new EntityItem(entity.world, entity.posX, entity.posY, entity.posZ, mobTrophy.getItemStack());
						drop.setDefaultPickupDelay();
						event.getDrops().add(drop);
					}
				}
			}
		}
	}

	private static boolean canDrop(Entity entity) {
		final World world = entity.world;
		return world != null && world.getGameRules().getBoolean("doMobLoot");
	}

}
