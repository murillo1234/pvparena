package net.slipcor.pvparena.goals;

import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.util.Vector;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.classes.PABlock;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Debug;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.loadables.ArenaRegion;
import net.slipcor.pvparena.loadables.ArenaRegion.RegionType;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.managers.TeamManager;
import net.slipcor.pvparena.runnables.EndRunnable;

/**
 * <pre>
 * Arena Goal class "BlockDestroy"
 * </pre>
 * 
 * Win by breaking the other team's block(s).
 * 
 * @author slipcor
 */

public class GoalBlockDestroy extends ArenaGoal implements Listener {

	public GoalBlockDestroy() {
		super("BlockDestroy");
		debug = new Debug(100);
	}

	private String blockTeamName = "";

	@Override
	public String version() {
		return PVPArena.instance.getDescription().getVersion();
	}

	private static final int PRIORITY = 9;

	@Override
	public boolean allowsJoinInBattle() {
		return arena.getArenaConfig().getBoolean(CFG.PERMS_JOININBATTLE);
	}

	public PACheck checkCommand(final PACheck res, final String string) {
		if (res.getPriority() > PRIORITY) {
			return res;
		}

		if (string.equalsIgnoreCase("blocktype")) {
			res.setPriority(this, PRIORITY);
		}

		for (ArenaTeam team : arena.getTeams()) {
			final String sTeam = team.getName();
			if (string.contains(sTeam + "block")) {
				res.setPriority(this, PRIORITY);
			}
		}

		return res;
	}

	@Override
	public PACheck checkEnd(final PACheck res) {

		if (res.getPriority() > PRIORITY) {
			return res;
		}

		final int count = TeamManager.countActiveTeams(arena);

		if (count == 1) {
			res.setPriority(this, PRIORITY); // yep. only one team left. go!
		} else if (count == 0) {
			res.setError(this, "No teams playing!");
		}

		return res;
	}

	@Override
	public String checkForMissingSpawns(final Set<String> list) {
		String team = checkForMissingTeamSpawn(list);
		if (team != null) {
			return team;
		}
		return this.checkForMissingTeamCustom(list, "block");
	}

	@Override
	public PACheck checkJoin(final CommandSender sender, final PACheck res, final String[] args) {
		if (res.getPriority() >= PRIORITY) {
			return res;
		}

		final int maxPlayers = arena.getArenaConfig().getInt(CFG.READY_MAXPLAYERS);
		final int maxTeamPlayers = arena.getArenaConfig().getInt(
				CFG.READY_MAXTEAMPLAYERS);

		if (maxPlayers > 0 && arena.getFighters().size() >= maxPlayers) {
			res.setError(this, Language.parse(arena, MSG.ERROR_JOIN_ARENA_FULL));
			return res;
		}

		if (args == null || args.length < 1) {
			return res;
		}

		if (!arena.isFreeForAll()) {
			final ArenaTeam team = arena.getTeam(args[0]);

			if (team != null && maxTeamPlayers > 0
						&& team.getTeamMembers().size() >= maxTeamPlayers) {
				res.setError(this, Language.parse(arena, MSG.ERROR_JOIN_TEAM_FULL));
				return res;
			}
		}

		res.setPriority(this, PRIORITY);
		return res;
	}

	@Override
	public PACheck checkSetBlock(final PACheck res, final Player player, final Block block) {

		if (res.getPriority() > PRIORITY
				|| !PAA_Region.activeSelections.containsKey(player.getName())) {
			return res;
		}
		if (block == null
				|| !block
						.getType()
						.name()
						.equals(arena.getArenaConfig().getString(
								CFG.GOAL_BLOCKDESTROY_BLOCKTYPE))) {
			return res;
		}

		if (!PVPArena.hasAdminPerms(player)
				&& !(PVPArena.hasCreatePerms(player, arena))) {
			return res;
		}
		res.setPriority(this, PRIORITY); // success :)

		return res;
	}

	private void commit(final Arena arena, final String sTeam, final boolean win) {
		arena.getDebugger().i("[BD] checking end: " + sTeam);
		arena.getDebugger().i("win: " + win);

		for (ArenaTeam team : arena.getTeams()) {
			if (team.getName().equals(sTeam) == win) {
				/*
				team is sTeam and win
				team is not sTeam and not win
				*/
				continue;
			}
			for (ArenaPlayer ap : team.getTeamMembers()) {
				if (ap.getStatus() == Status.FIGHT || ap.getStatus() == Status.DEAD) {
					//ap.addStatistic(arena.getName(), type.LOSSES, 1);
					/*
					arena.removePlayer(ap.get(), CFG.TP_LOSE.toString(),
							true, false);*/
					
					ap.setStatus(Status.LOST);
					
					//ap.setTelePass(false);
				}
			}
		}
		/*
		if (!win && getLifeMap().size() > 1) {
			return; // if not a win trigger AND more than one team left. out!
		}
		
		for (ArenaTeam team : arena.getTeams()) {
			for (ArenaPlayer ap : team.getTeamMembers()) {
				if (!ap.getStatus().equals(Status.FIGHT)) {
					continue;
				}
				winteam = team.getName();
				break;
			}
		}

		if (arena.getTeam(winteam) != null) {

			ArenaModuleManager
					.announce(
							arena,
							Language.parse(arena, MSG.TEAM_HAS_WON,
									arena.getTeam(winteam).getColor()
											+ winteam + ChatColor.YELLOW),
							"WINNER");
			arena.broadcast(Language.parse(arena, MSG.TEAM_HAS_WON,
					arena.getTeam(winteam).getColor() + winteam
							+ ChatColor.YELLOW));
		}

		getLifeMap().clear();
		new EndRunnable(arena, arena.getArenaConfig().getInt(
				CFG.TIME_ENDCOUNTDOWN));
				*/
		PACheck.handleEnd(arena, false);
	}

	@Override
	public void commitCommand(final CommandSender sender, final String[] args) {
		if (args[0].equalsIgnoreCase("blocktype")) {
			if (args.length < 2) {
				arena.msg(
						sender,
						Language.parse(arena, MSG.ERROR_INVALID_ARGUMENT_COUNT,
								String.valueOf(args.length), "2"));
				return;
			}

			try {
				final int value = Integer.parseInt(args[1]);
				arena.getArenaConfig().set(CFG.GOAL_BLOCKDESTROY_BLOCKTYPE,
						Material.getMaterial(value).name());
			} catch (Exception e) {
				final Material mat = Material.getMaterial(args[1].toUpperCase());

				if (mat == null) {
					arena.msg(sender,
							Language.parse(arena, MSG.ERROR_MAT_NOT_FOUND, args[1]));
					return;
				}

				arena.getArenaConfig().set(CFG.GOAL_BLOCKDESTROY_BLOCKTYPE,
						mat.name());
			}
			arena.getArenaConfig().save();
			arena.msg(sender, Language.parse(arena, MSG.GOAL_BLOCKDESTROY_TYPESET,
					CFG.GOAL_BLOCKDESTROY_BLOCKTYPE.toString()));

		} else if (args[0].contains("block")) {
			for (ArenaTeam team : arena.getTeams()) {
				final String sTeam = team.getName();
				if (args[0].contains(sTeam + "block")) {
					blockTeamName = args[0];
					PAA_Region.activeSelections.put(sender.getName(), arena);

					arena.msg(sender, Language.parse(arena,
							MSG.GOAL_BLOCKDESTROY_TOSET, blockTeamName));
				}
			}
		}
	}

	@Override
	public void commitEnd(final boolean force) {
		arena.getDebugger().i("[BD]");

		PAGoalEvent gEvent = new PAGoalEvent(arena, this, "");
		Bukkit.getPluginManager().callEvent(gEvent);
		ArenaTeam aTeam = null;

		for (ArenaTeam team : arena.getTeams()) {
			for (ArenaPlayer ap : team.getTeamMembers()) {
				if (ap.getStatus().equals(Status.FIGHT)) {
					aTeam = team;
					break;
				}
			}
		}

		if (aTeam != null && !force) {

			ArenaModuleManager.announce(
					arena,
					Language.parse(arena, MSG.TEAM_HAS_WON, aTeam.getColor()
							+ aTeam.getName() + ChatColor.YELLOW), "WINNER");
			arena.broadcast(Language.parse(arena, MSG.TEAM_HAS_WON, aTeam.getColor()
					+ aTeam.getName() + ChatColor.YELLOW));
		}

		if (ArenaModuleManager.commitEnd(arena, aTeam)) {
			return;
		}
		new EndRunnable(arena, arena.getArenaConfig().getInt(
				CFG.TIME_ENDCOUNTDOWN));
	}

	@Override
	public boolean commitSetFlag(final Player player, final Block block) {

		arena.getDebugger().i("trying to set a block", player);

		// command : /pa redblock1
		// location: red1block:

		SpawnManager.setBlock(arena, new PABlockLocation(block.getLocation()),
				blockTeamName);

		arena.msg(player,
				Language.parse(arena, MSG.GOAL_BLOCKDESTROY_SET, blockTeamName));

		PAA_Region.activeSelections.remove(player.getName());
		blockTeamName = "";

		return true;
	}

	@Override
	public void commitStart() {
		
	}

	@Override
	public void configParse(final YamlConfiguration config) {
		Bukkit.getPluginManager().registerEvents(this, PVPArena.instance);
	}

	@Override
	public PACheck getLives(final PACheck res, final ArenaPlayer aPlayer) {
		if (res.getPriority() <= PRIORITY+1000) {
			res.setError(
					this,
					String.valueOf(getLifeMap().containsKey(aPlayer.getArenaTeam()
									.getName()) ? getLifeMap().get(aPlayer
									.getArenaTeam().getName()) : 0));
		}
		return res;
	}
	
	@Override
	public void displayInfo(CommandSender sender) {
		sender.sendMessage("block type: " + 
				arena.getArenaConfig().getString(CFG.GOAL_BLOCKDESTROY_BLOCKTYPE));
		sender.sendMessage("lives: " + 
				arena.getArenaConfig().getInt(CFG.GOAL_BLOCKDESTROY_LIVES));
	}

	@Override
	public boolean hasSpawn(final String string) {
		for (String teamName : arena.getTeamNames()) {
			if (string.toLowerCase().equals(teamName.toLowerCase() + "block")) {
				return true;
			}
			if (string.toLowerCase().startsWith(
					teamName.toLowerCase() + "spawn")) {
				return true;
			}

			if (arena.getArenaConfig().getBoolean(CFG.GENERAL_CLASSSPAWN)) {
				for (ArenaClass aClass : arena.getClasses()) {
					if (string.toLowerCase().startsWith(teamName.toLowerCase() + 
							aClass.getName().toLowerCase() + "spawn")) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public void initate(final Player player) {
		final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
		final ArenaTeam team = aPlayer.getArenaTeam();
		if (!getLifeMap().containsKey(team.getName())) {
			getLifeMap().put(aPlayer.getArenaTeam().getName(), arena.getArenaConfig()
					.getInt(CFG.GOAL_BLOCKDESTROY_LIVES));
			
			final Set<PABlockLocation> blocks = SpawnManager.getBlocksContaining(arena, "block");
			
			for (PABlockLocation block : blocks) {
				takeBlock(team.getColor().name(), false, block);
			}
		}
	}

	@Override
	public boolean isInternal() {
		return true;
	}

	@Override
	public void parseStart() {
		getLifeMap().clear();
		for (ArenaTeam team : arena.getTeams()) {
			if (team.getTeamMembers().size() > 0) {
				arena.getDebugger().i("adding team " + team.getName());
				// team is active
				getLifeMap().put(
						team.getName(),
						arena.getArenaConfig().getInt(
								CFG.GOAL_BLOCKDESTROY_LIVES, 1));
			}
			final Set<PABlockLocation> blocks = SpawnManager.getBlocksContaining(arena, "block");
			
			for (PABlockLocation block : blocks) {
				takeBlock(team.getColor().name(), false, block);
			}
		}
	}

	private boolean reduceLivesCheckEndAndCommit(final Arena arena, final String team) {

		arena.getDebugger().i("reducing lives of team " + team);
		final int count = getLifeMap().get(team) - 1;
		if (count > 0) {
			getLifeMap().put(team, count);
		} else {
			getLifeMap().remove(team);
			commit(arena, team, false);
			return true;
		}
		return false;
	}

	@Override
	public void reset(final boolean force) {
		getLifeMap().clear();
	}

	@Override
	public void setDefaults(final YamlConfiguration config) {
		if (arena.isFreeForAll()) {
			return;
		}

		if (config.get("teams.free") != null) {
			config.set("teams", null);
		}
		if (config.get("teams") == null) {
			arena.getDebugger().i("no teams defined, adding custom red and blue!");
			config.addDefault("teams.red", ChatColor.RED.name());
			config.addDefault("teams.blue", ChatColor.BLUE.name());
		}
	}

	/**
	 * take/reset an arena block
	 * 
	 * @param blockColor
	 *            the teamcolor to reset
	 * @param take
	 *            true if take, else reset
	 * @param pumpkin
	 *            true if pumpkin, false otherwise
	 * @param paBlockLocation
	 *            the location to take/reset
	 */
	public void takeBlock(final String blockColor, final boolean take, final PABlockLocation paBlockLocation) {
		if (paBlockLocation == null) {
			return;
		}
		if (arena.getArenaConfig().getString(CFG.GOAL_BLOCKDESTROY_BLOCKTYPE)
				.equals("WOOL")) {
			paBlockLocation.toLocation()
					.getBlock()
					.setTypeIdAndData(
							Material.valueOf(
									arena.getArenaConfig().getString(
											CFG.GOAL_BLOCKDESTROY_BLOCKTYPE))
									.getId(),
							StringParser.getColorDataFromENUM(blockColor),
							false);
		} else {
			paBlockLocation.toLocation()
					.getBlock()
					.setTypeId(
							Material.valueOf(
									arena.getArenaConfig().getString(
											CFG.GOAL_BLOCKDESTROY_BLOCKTYPE))
									.getId());
		}
	}

	@Override
	public Map<String, Double> timedEnd(final Map<String, Double> scores) {
		double score;

		for (ArenaTeam team : arena.getTeams()) {
			score = (getLifeMap().containsKey(team.getName()) ? getLifeMap()
					.get(team.getName()) : 0);
			if (scores.containsKey(team)) {
				scores.put(team.getName(), scores.get(team.getName()) + score);
			} else {
				scores.put(team.getName(), score);
			}
		}

		return scores;
	}

	@Override
	public void unload(final Player player) {
		disconnect(ArenaPlayer.parsePlayer(player.getName()));
		if (allowsJoinInBattle()) {
			arena.hasNotPlayed(ArenaPlayer.parsePlayer(player.getName()));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void onBlockBreak(final BlockBreakEvent event) {
		final Player player = event.getPlayer();
		if (!arena.hasPlayer(event.getPlayer())
				|| !event
						.getBlock()
						.getType()
						.name()
						.equals(arena.getArenaConfig().getString(
								CFG.GOAL_BLOCKDESTROY_BLOCKTYPE))) {

			arena.getDebugger().i("block destroy, ignoring", player);
			arena.getDebugger().i(String.valueOf(arena.hasPlayer(event.getPlayer())), player);
			arena.getDebugger().i(event.getBlock().getType().name(), player);
			return;
		}

		if (!arena.isFightInProgress()) {
			event.setCancelled(true);
			return;
		}
		
		final Block block = event.getBlock();

		arena.getDebugger().i("block destroy!", player);

		Vector vLoc;
		Vector vBlock = null;
		final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

		final ArenaTeam pTeam = aPlayer.getArenaTeam();
		if (pTeam == null) {
			return;
		}

		PAGoalEvent gEvent = new PAGoalEvent(arena, this, "trigger:"+player.getName());
		Bukkit.getPluginManager().callEvent(gEvent);
		for (ArenaTeam team : arena.getTeams()) {
			final String blockTeam = team.getName();

			if (team.getTeamMembers().size() < 1
					&& !team.getName().equals("touchdown")) {
				arena.getDebugger().i("size!OUT! ", player);
				continue; // dont check for inactive teams
			}

			arena.getDebugger().i("checking for block of team " + blockTeam, player);
			vLoc = block.getLocation().toVector();
			arena.getDebugger().i("block: " + vLoc.toString(), player);
			if (SpawnManager.getBlocksStartingWith(arena, blockTeam + "block").size() > 0) {
				vBlock = SpawnManager
						.getBlockNearest(
								SpawnManager.getBlocksStartingWith(arena, blockTeam
										+ "block"),
								new PABlockLocation(player.getLocation()))
						.toLocation().toVector();
			}
			if ((vBlock != null) && (vLoc.distance(vBlock) < 2)) {

				// ///////

				if (blockTeam.equals(pTeam.getName())) {
					arena.getDebugger().i("is own team! cancel and OUT! ", player);
					event.setCancelled(true);
					continue;
				}

				final String sTeam = pTeam.getName();

				try {
					arena.broadcast(Language.parse(arena, MSG.GOAL_BLOCKDESTROY_SCORE,
							arena.getTeam(sTeam).colorizePlayer(player)
									+ ChatColor.YELLOW, arena
									.getTeam(blockTeam).getColoredName()
									+ ChatColor.YELLOW, String
									.valueOf(getLifeMap().get(blockTeam) - 1)));
				} catch (Exception e) {
					Bukkit.getLogger().severe(
							"[PVP Arena] team unknown/no lives: " + blockTeam);
					e.printStackTrace();
				}
				class RunLater implements Runnable  {
					String localColor;
					PABlockLocation localLoc;
					RunLater(String color, PABlockLocation loc) {
						localColor = color;
						localLoc = loc;
					}
					
					@Override
					public void run() {
						takeBlock(localColor, false,
								localLoc);
					}
				}
				
				if (this.getLifeMap().containsKey(blockTeam)
						&& getLifeMap().get(blockTeam) > SpawnManager.getBlocksStartingWith(arena, blockTeam + "block").size()) {
				
					Bukkit.getScheduler().runTaskLater(
							PVPArena.instance,
							new RunLater(
									arena.getTeam(blockTeam).getColor().name(),
									new PABlockLocation(event.getBlock().getLocation())), 5L);
				}
				reduceLivesCheckEndAndCommit(arena, blockTeam);

				return;
			}
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityExplode(final EntityExplodeEvent event) {
		if (arena == null) {
			return;
		}
		
		boolean contains = false;
		
		for (ArenaRegion region : arena.getRegionsByType(RegionType.BATTLE)) {
			if (region.getShape().contains(new PABlockLocation(event.getLocation()))) {
				contains = true;
				break;
			}
		}
		
		if (!contains) {
			return;
		}

		Set<PABlock> blocks = SpawnManager.getPABlocksContaining(arena, "block");
		
		//final Set<PABlockLocation>
		
		for (Block b : event.blockList()) {
			PABlockLocation loc = new PABlockLocation(b.getLocation());
			for (PABlock pb : blocks) {
				if (pb.getLocation().getDistanceSquared(loc) < 1) {
					final String blockTeam = pb.getName().split("block")[0];
					
					try {
						arena.broadcast(Language.parse(arena, MSG.GOAL_BLOCKDESTROY_SCORE,
								Language.parse(arena, MSG.DEATHCAUSE_BLOCK_EXPLOSION)
										+ ChatColor.YELLOW, arena
										.getTeam(blockTeam).getColoredName()
										+ ChatColor.YELLOW, String
										.valueOf(getLifeMap().get(blockTeam) - 1)));
					} catch (Exception e) {
						Bukkit.getLogger().severe(
								"[PVP Arena] team unknown/no lives: " + blockTeam);
						e.printStackTrace();
					}
					takeBlock(arena.getTeam(blockTeam).getColor().name(), false,
							pb.getLocation());

					reduceLivesCheckEndAndCommit(arena, blockTeam);
				}
			}
		}
	}
}
