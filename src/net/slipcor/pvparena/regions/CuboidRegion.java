package net.slipcor.pvparena.regions;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.loadables.ArenaRegion;
import net.slipcor.pvparena.loadables.ArenaRegionShape;

/**
 * <pre>
 * Arena Region Shape class "cuboid"
 * </pre>
 * 
 * Defines a cuboid region, including overlap checks and contain checks
 * 
 * @author slipcor
 */

public class CuboidRegion extends ArenaRegionShape {

	private final Set<Block> border = new HashSet<Block>();
	private ArenaRegion region;

	public CuboidRegion() {
		super("cuboid");
	}
	
	@Override
	public String version() {
		return PVPArena.instance.getDescription().getVersion();
	}

	/**
	 * sanitize a pair of locations
	 * 
	 * @param lMin
	 *            the minimum point
	 * @param lMax
	 *            the maximum point
	 * @return a recalculated pair of locations
	 */
	private PABlockLocation[] sanityCheck(final PABlockLocation lMin,
			final PABlockLocation lMax) {
		final boolean x = (lMin.getX() > lMax.getX());
		final boolean y = (lMin.getY() > lMax.getY());
		final boolean z = (lMin.getZ() > lMax.getZ());

		if (!(x | y | z)) {
			return new PABlockLocation[] { lMin, lMax };
		}
		final PABlockLocation l1 = new PABlockLocation(lMin.getWorldName(), x ? lMax.getX()
				: lMin.getX(), y ? lMax.getY() : lMin.getY(), z ? lMax.getZ()
				: lMin.getZ());
		final PABlockLocation l2 = new PABlockLocation(lMin.getWorldName(), x ? lMin.getX()
				: lMax.getX(), y ? lMin.getY() : lMax.getY(), z ? lMin.getZ()
				: lMax.getZ());

		return new PABlockLocation[] { l1, l2 };
	}

	public final void initialize(ArenaRegion region) {
		this.region = region;
		final PABlockLocation[] sane = sanityCheck(region.locs[0], region.locs[1]);
		region.locs[0] = sane[0];
		region.locs[1] = sane[1];
	}

	@Override
	public boolean overlapsWith(final ArenaRegion paRegion) {
		if (paRegion.getShape() instanceof CuboidRegion) {
			// compare 2 cuboids
			if (getMinimumLocation().getX() > paRegion.locs[1].getX()
					|| getMinimumLocation().getY() > paRegion.locs[1].getY()
					|| getMinimumLocation().getZ() > paRegion.locs[1].getZ()) {
				return false;
			}
			if (paRegion.locs[0].getX() > getMaximumLocation().getX()
					|| paRegion.locs[0].getY() > getMaximumLocation().getY()
					|| paRegion.locs[0].getZ() > getMaximumLocation().getZ()) {
				return false;
			}
			return true;
		} else if (paRegion.getShape() instanceof SphericRegion) {
			// we are cube and search for intersecting sphere

			final PABlockLocation thisCenter = getMaximumLocation().getMidpoint(getMinimumLocation());
			final PABlockLocation thatCenter = paRegion.locs[1]
					.getMidpoint(paRegion.locs[0]);

			final Double thatRadius = paRegion.locs[0].getDistance(paRegion
					.locs[1]) / 2;

			if (contains(thatCenter)) {
				return true; // the sphere is inside!
			}

			final PABlockLocation offset = thatCenter.pointTo(thisCenter, thatRadius);
			// offset is pointing from that to this

			return this.contains(offset);
		} else if (paRegion.getShape() instanceof CylindricRegion) {
			// we are cube and search for intersecting cylinder

			final PABlockLocation thisCenter = getMaximumLocation().getMidpoint(
					getMinimumLocation());
			final PABlockLocation thatCenter = paRegion.locs[1]
					.getMidpoint(paRegion.locs[0]);

			if (getMaximumLocation().getY() < paRegion.locs[0].getY()) {
				return false;
			}
			if (getMinimumLocation().getY() > paRegion.locs[1].getY()) {
				return false;
			}

			thisCenter.setY(thatCenter.getY());

			if (contains(thatCenter)) {
				return true; // the sphere is inside!
			}

			final Double thatRadius = paRegion.locs[0].getDistance(paRegion
					.locs[1]) / 2;

			final PABlockLocation offset = thatCenter.pointTo(thisCenter, thatRadius);
			// offset is pointing from that to this

			return this.contains(offset);
		} else {
			PVPArena.instance.getLogger()
					.warning(
							"Region Shape not supported: "
									+ paRegion.getShape().getName());
		}
		return false;
	}

	@Override
	public void showBorder(final Player player) {

		final Location min = getMinimumLocation().toLocation();
		final Location max = getMaximumLocation().toLocation();
		final World w = Bukkit.getWorld(getRegion().getWorldName());

		border.clear();

		// move along exclusive x, create miny+maxy+minz+maxz
		for (int x = min.getBlockX() + 1; x < max.getBlockX(); x++) {
			border.add(new Location(w, x, min.getBlockY(), min.getBlockZ())
					.getBlock());
			border.add(new Location(w, x, min.getBlockY(), max.getBlockZ())
					.getBlock());
			border.add(new Location(w, x, max.getBlockY(), min.getBlockZ())
					.getBlock());
			border.add(new Location(w, x, max.getBlockY(), max.getBlockZ())
					.getBlock());
		}
		// move along exclusive y, create minx+maxx+minz+maxz
		for (int y = min.getBlockY() + 1; y < max.getBlockY(); y++) {
			border.add(new Location(w, min.getBlockX(), y, min.getBlockZ())
					.getBlock());
			border.add(new Location(w, min.getBlockX(), y, max.getBlockZ())
					.getBlock());
			border.add(new Location(w, max.getBlockX(), y, min.getBlockZ())
					.getBlock());
			border.add(new Location(w, max.getBlockX(), y, max.getBlockZ())
					.getBlock());
		}
		// move along inclusive z, create minx+maxx+miny+maxy
		for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
			border.add(new Location(w, min.getBlockX(), min.getBlockY(), z)
					.getBlock());
			border.add(new Location(w, min.getBlockX(), max.getBlockY(), z)
					.getBlock());
			border.add(new Location(w, max.getBlockX(), min.getBlockY(), z)
					.getBlock());
			border.add(new Location(w, max.getBlockX(), max.getBlockY(), z)
					.getBlock());
		}

		for (Block b : border) {
			if (!getRegion().isInNoWoolSet(b)) {
				player.sendBlockChange(b.getLocation(), Material.WOOL, (byte) 0);
			}
		}

		Bukkit.getScheduler().scheduleSyncDelayedTask(PVPArena.instance,
				new Runnable() {

					@Override
					public void run() {
						for (Block b : border) {
							player.sendBlockChange(b.getLocation(),
									b.getTypeId(), b.getData());
						}
						border.clear();
					}

				}, 100L);
	}

	@Override
	public boolean contains(final PABlockLocation loc) {
		if (getMinimumLocation() == null || getMaximumLocation() == null
				|| loc == null || !loc.getWorldName().equals(getRegion().getWorldName())) {
			return false; // no arena, no container or not in the same world
		}
		return loc.isInAABB(getMinimumLocation(), getMaximumLocation());
	}

	@Override
	public PABlockLocation getCenter() {
		return getMinimumLocation().getMidpoint(getMaximumLocation());
	}

	@Override
	public PABlockLocation getMaximumLocation() {
		return getRegion().locs[1];
	}

	@Override
	public PABlockLocation getMinimumLocation() {
		return getRegion().locs[0];
	}
	
	public ArenaRegion getRegion() {
		return region;
	}

	@Override
	public boolean tooFarAway(final int joinRange, final Location location) {
		final PABlockLocation reach = (new PABlockLocation(location)).pointTo(
				getCenter(), (double) joinRange);

		return contains(reach);
	}

	@Override
	public void move(BlockFace direction, int value) {
		final int diffX = direction.getModX();
		final int diffY = direction.getModY();
		final int diffZ = direction.getModZ();
		
		if (diffX == 0 && diffY == 0 && diffZ == 0) {
			return;
		}
		region.locs[0] = new PABlockLocation(region.locs[0].toLocation().add(diffX*value, diffY*value, diffZ*value));
		region.locs[1] = new PABlockLocation(region.locs[1].toLocation().add(diffX*value, diffY*value, diffZ*value));
	}

	@Override
	public void extend(BlockFace direction, int value) {
		final int diffX = direction.getModX();
		final int diffY = direction.getModY();
		final int diffZ = direction.getModZ();
		
		if (diffX == 0 && diffY == 0 && diffZ == 0) {
			return;
		}
		
		if (diffX > 0) {
			region.locs[1] = new PABlockLocation(region.locs[1].toLocation().add(diffX*value, 0, 0));
		} else if (diffX < 0) {
			region.locs[0] = new PABlockLocation(region.locs[0].toLocation().subtract(diffX*value, 0, 0));
		}
		
		if (diffY > 0) {
			region.locs[1] = new PABlockLocation(region.locs[1].toLocation().add(0, diffY*value, 0));
		} else if (diffY < 0) {
			region.locs[0] = new PABlockLocation(region.locs[0].toLocation().subtract(0, diffY*value, 0));
		}
		
		if (diffZ > 0) {
			region.locs[1] = new PABlockLocation(region.locs[1].toLocation().add(0, 0, diffZ*value));
		} else if (diffZ < 0) {
			region.locs[0] = new PABlockLocation(region.locs[0].toLocation().subtract(0, 0, diffZ*value));
		}
		
	}
}
