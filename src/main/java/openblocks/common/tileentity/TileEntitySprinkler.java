package openblocks.common.tileentity;

import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidHandler;
import openblocks.Config;
import openblocks.OpenBlocks;
import openblocks.client.gui.GuiSprinkler;
import openblocks.common.container.ContainerSprinkler;
import openmods.api.IHasGui;
import openmods.api.INeighbourAwareTile;
import openmods.api.ISurfaceAttachment;
import openmods.fakeplayer.FakePlayerPool;
import openmods.fakeplayer.FakePlayerPool.PlayerUser;
import openmods.fakeplayer.OpenModsFakePlayer;
import openmods.include.IncludeInterface;
import openmods.include.IncludeOverride;
import openmods.inventory.GenericInventory;
import openmods.inventory.IInventoryProvider;
import openmods.inventory.TileEntityInventory;
import openmods.inventory.legacy.ItemDistribution;
import openmods.liquids.GenericFluidHandler;
import openmods.sync.SyncableFlags;
import openmods.sync.SyncableTank;
import openmods.tileentity.SyncedTileEntity;

import net.minecraftforge.common.IPlantable;
import net.minecraft.block.IGrowable;

public class TileEntitySprinkler extends SyncedTileEntity implements ISurfaceAttachment, IInventoryProvider, IHasGui, INeighbourAwareTile {

	private static final ItemStack BONEMEAL = new ItemStack(Items.dye, 1, 15);

	private static final Random RANDOM = new Random();

	private static final double[] SPRINKER_DELTA = new double[] { 0.2, 0.25, 0.5 };
	private static final int[] SPRINKER_MOD = new int[] { 1, 5, 20 };

	private boolean hasBonemeal = false;

	private boolean needsTankUpdate;

	public enum Flags {
		enabled
	}

	private SyncableFlags flags;
	private SyncableTank tank;

	public int ticks;

	private final GenericInventory inventory = registerInventoryCallback(new TileEntityInventory(this, "sprinkler", true, 9) {
		@Override
		public boolean isItemValidForSlot(int i, ItemStack itemstack) {
			return itemstack != null && itemstack.isItemEqual(BONEMEAL);
		}
	});

	@IncludeInterface
	private final IFluidHandler tankWrapper = new GenericFluidHandler.Drain(tank);

	@Override
	protected void createSyncedFields() {
		flags = SyncableFlags.create(Flags.values().length);
		tank = new SyncableTank(Config.sprinklerInternalTank, FluidRegistry.WATER, OpenBlocks.Fluids.xpJuice);
	}

	private void attemptFertilize() {
		if (!(worldObj instanceof WorldServer)) return;
		growCropsNearby((WorldServer) worldObj, xCoord, yCoord, zCoord);
	}

	private int tileRange() {
		return Config.sprinklerEffectiveRange;
	}

	private int secondsBetweenGrowthTicks() {
		return 5;
	}

	public void growCropsNearby(WorldServer world, int xO, int yO, int zO) {
        for (int xD = -tileRange(); xD <= tileRange(); xD++) {
            for (int yD = -1; yD <= tileRange(); yD++) {
                for (int zD = -tileRange(); zD <= tileRange(); zD++) {
                    int x = xO + xD;
                    int y = yO + yD;
                    int z = zO + zD;

                    double distance = Math.sqrt(Math.pow(x-xO, 2) + Math.pow(y - yO,2) + Math.pow(z - zO,2));
                    // distance -= fullPotencyRange();
                    distance = Math.min(1D, distance);
                    double distanceCoefficient = 1D - (distance / tileRange());

                    Block block = world.getBlock(x, y, z);

                    if (block instanceof IPlantable || block instanceof IGrowable) {
						//it schedules the next tick.
						world.scheduleBlockUpdate(x, y, z, block, (int) (distanceCoefficient * (float) secondsBetweenGrowthTicks() * 20F));
						block.updateTick(world, x, y, z, world.rand);
                    }
                }
            }
        }
        world.scheduleBlockUpdate(xO, yO, zO, world.getBlock(xO, yO, zO), secondsBetweenGrowthTicks() * 20);
    }

	@Override
	public Object getServerGui(EntityPlayer player) {
		return new ContainerSprinkler(player.inventory, this);
	}

	@Override
	public Object getClientGui(EntityPlayer player) {
		return new GuiSprinkler(new ContainerSprinkler(player.inventory, this));
	}

	@Override
	public boolean canOpenGui(EntityPlayer player) {
		return true;
	}

	private static final double SPRAY_SIDE_SCATTER = Math.toRadians(25);

	private void sprayParticles() {
		if (tank.getFluidAmount() > 0) {
			// 0 = All, 1 = Decreased, 2 = Minimal
			final int particleSetting = OpenBlocks.proxy.getParticleSettings();
			if (particleSetting > 2) return;

			final int fillFactor = SPRINKER_MOD[particleSetting];

			if ((ticks % fillFactor) != 0) return;
			final ForgeDirection blockYawRotation = getOrientation().north();
			final double nozzleAngle = getSprayDirection();
			final double sprayForwardVelocity = Math.sin(Math.toRadians(nozzleAngle * 25));

			final double forwardVelocityX = sprayForwardVelocity * blockYawRotation.offsetZ / -2;
			final double forwardVelocityZ = sprayForwardVelocity * blockYawRotation.offsetX / 2;

			final double sprinklerDelta = SPRINKER_DELTA[particleSetting];
			double outletPosition = -0.5;

			while (outletPosition <= 0.5) {
				final double spraySideVelocity = Math.sin(SPRAY_SIDE_SCATTER * (RANDOM.nextDouble() - 0.5));

				final double sideVelocityX = spraySideVelocity * blockYawRotation.offsetX;
				final double sideVelocityZ = spraySideVelocity * blockYawRotation.offsetZ;

				Vec3 vec = Vec3.createVectorHelper(
						forwardVelocityX + sideVelocityX,
						0.35,
						forwardVelocityZ + sideVelocityZ);

				OpenBlocks.proxy.spawnLiquidSpray(worldObj, tank.getFluid().getFluid(),
						xCoord + 0.5 + (outletPosition * 0.6 * blockYawRotation.offsetX),
						yCoord + 0.2,
						zCoord + 0.5 + (outletPosition * 0.6 * blockYawRotation.offsetZ),
						0.3f, 0.7f, vec);

				outletPosition += sprinklerDelta;
			}
		}
	}

	@Override
	public void updateEntity() {
		super.updateEntity();

		if (!worldObj.isRemote) {

			if (tank.getFluidAmount() <= 0) {
				if (needsTankUpdate) {
					tank.updateNeighbours(worldObj, getPosition());
					needsTankUpdate = false;
				}

				tank.fillFromSide(worldObj, getPosition(), ForgeDirection.DOWN);
			}

			if (ticks % Config.sprinklerBonemealConsumeRate == 0) {
				hasBonemeal = ItemDistribution.consumeFirstInventoryItem(inventory, BONEMEAL);
			}

			if (ticks % Config.sprinklerWaterConsumeRate == 0) {
				setEnabled(tank.drain(1, true) != null);
				sync();
			}
		}

		ticks++;

		// simplified this action because only one of these will execute
		// depending on worldObj.isRemote
		if (isEnabled()) {
			if (worldObj.isRemote) sprayParticles();
			else attemptFertilize();
		}
	}

	private void setEnabled(boolean b) {
		flags.set(Flags.enabled, b);
	}

	private boolean isEnabled() {
		return flags.get(Flags.enabled);
	}

	@Override
	public ForgeDirection getSurfaceDirection() {
		return ForgeDirection.DOWN;
	}

	/**
	 * Get spray direction of Sprinkler particles
	 *
	 * @return float from -1f to 1f indicating the direction, left to right of the particles
	 */
	public float getSprayDirection() {
		if (isEnabled()) { return MathHelper.sin(ticks * 0.02f); }
		return 0;
	}

	@IncludeOverride
	public boolean canDrain(ForgeDirection from, Fluid fluid) {
		return false;
	}

	@Override
	@IncludeInterface
	public IInventory getInventory() {
		return inventory;
	}

	@Override
	public void writeToNBT(NBTTagCompound tag) {
		super.writeToNBT(tag);
		inventory.writeToNBT(tag);
	}

	@Override
	public void readFromNBT(NBTTagCompound tag) {
		super.readFromNBT(tag);
		inventory.readFromNBT(tag);
	}

	@Override
	public void validate() {
		super.validate();
		this.needsTankUpdate = true;
	}

	@Override
	public void onNeighbourChanged(Block block) {
		this.needsTankUpdate = true;
	}
}
