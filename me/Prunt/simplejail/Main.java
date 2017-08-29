package me.Prunt.simplejail;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

    @SuppressWarnings("deprecation")
    @Override
    public void onEnable() {
	// Registers events
	getServer().getPluginManager().registerEvents(this, this);

	// Copys default config if it doesn't exist
	saveDefaultConfig();

	// convert uuid <-> playernames
	if (getConfig().contains("players")) {
	    for (String key : getConfig().getConfigurationSection("players").getKeys(false)) {
		if ((isUUID() && !key.contains("-")) || (!isUUID() && key.contains("-"))) {
		    OfflinePlayer op;

		    if (isUUID()) {
			op = getServer().getOfflinePlayer(key);
		    } else {
			op = getServer().getOfflinePlayer(UUID.fromString(key));
		    }

		    if (op.hasPlayedBefore()) {
			Object o = getConfig().get("players." + key);
			getConfig().set("players." + key, null);
			getConfig().set("players." + getName(op), o);
		    } else {
			if (isUUID()) {
			    getServer().getLogger().warning("Error converting player name " + key + " to UUID!");
			} else {
			    getServer().getLogger().warning("Error converting UUID " + key + " to player name!");
			}
		    }
		}
	    }
	}
	if (getConfig().contains("jailed")) {
	    List<String> list = new ArrayList<>();
	    list.addAll(getConfig().getStringList("jailed"));

	    for (String key : getConfig().getStringList("jailed")) {
		if ((isUUID() && !key.contains("-")) || (!isUUID() && key.contains("-"))) {
		    OfflinePlayer op;

		    if (isUUID()) {
			op = getServer().getOfflinePlayer(key);
		    } else {
			op = getServer().getOfflinePlayer(UUID.fromString(key));
		    }

		    if (op.hasPlayedBefore()) {
			list.remove(key);
			list.add(getName(op));
		    }
		}
	    }

	    getConfig().set("jailed", list);
	}
	if (getConfig().contains("unjailed")) {
	    List<String> list = new ArrayList<>();
	    list.addAll(getConfig().getStringList("unjailed"));

	    for (String key : getConfig().getStringList("unjailed")) {
		if ((isUUID() && !key.contains("-")) || (!isUUID() && key.contains("-"))) {
		    OfflinePlayer op;

		    if (isUUID()) {
			op = getServer().getOfflinePlayer(key);
		    } else {
			op = getServer().getOfflinePlayer(UUID.fromString(key));
		    }

		    if (op.hasPlayedBefore()) {
			list.remove(key);
			list.add(getName(op));
		    }
		}
	    }

	    getConfig().set("unjailed", list);
	}
	saveConfig();

	// Creates scheduler to unjail players when necessary every 1 minute
	getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
	    @Override
	    public void run() {

		// Gets current Unix Timestamp
		long epoch = System.currentTimeMillis() / 1000;

		// Loops through online players
		for (Player p : getServer().getOnlinePlayers()) {
		    // Checks if player is jailed, but should already be
		    // unjailed
		    if (isJailed(p) && epoch > getConfig().getLong("players." + getName(p) + ".releasetime")) {
			// Releases player from jail
			unjail(p);
		    }
		}

	    }
	}, 0, 1200);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent e) {

	// Gets Player and their name
	Player p = e.getPlayer();

	// Checks if player is jailed
	if (isJailed(p)) {

	    // Checks if player has been jailed while offline
	    if (jailedOffline(p)) {
		// Sends them to jail again
		toJailAgain(p);

		// Remove from config
		removeJailed(p);
	    }

	    // Checks if player has been unjailed while offline
	    if (unjailedOffline(p)) {
		// Unjails the player
		unjail(p);

		// Remove from config
		removeUnjailed(p);
	    }

	    // Checks if teleporting to jail on login is true
	    if (getConfig().getBoolean("options.jail-on-login")) {
		// Sends them to jail again
		toJailAgain(p);
	    }

	}
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent e) {

	// Gets Player and their name
	Player p = e.getPlayer();

	// Checks if player is jailed
	if (isJailed(p)) {
	    // Sends them to jail again
	    toJailAgain(p);
	    e.setRespawnLocation(getJailLoc());
	}

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent e) {

	// Checks if event is already cancelled
	if (e.isCancelled()) {
	    return;
	}

	Player p = e.getPlayer();

	if (isJailed(p)) {

	    // Gets list of commands from config
	    List<String> commands = getConfig().getStringList("commands.filtered-list");
	    // Defines match boolean
	    boolean match = false;

	    // Loops through commands
	    for (String command : commands) {
		// Checks if the command matches with the one from config
		if (e.getMessage().startsWith("/" + command)) {
		    // Sets match to true
		    match = true;
		    break;
		}
	    }

	    // Checks if whitelist is on
	    if (getConfig().getString("commands.filter").equalsIgnoreCase("whitelist")) {
		// Checks if match is false
		if (!match) {
		    // Sends error message
		    p.sendMessage(getMessage("messages.cant-use-this-command"));
		    e.setCancelled(true);
		    return;
		}

		// Checks if blacklist is on
	    } else if (getConfig().getString("commands.filter").equalsIgnoreCase("blacklist")) {
		// Checks if match is true
		if (match) {
		    // Sends error message
		    p.sendMessage(getMessage("messages.cant-use-this-command"));
		    e.setCancelled(true);
		    return;
		}
	    }

	}
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

	if (cmd.getName().equalsIgnoreCase("jail")) {

	    // Checks if there's enough arguments
	    if (args.length < 3) {
		// Sends error message
		sender.sendMessage(getMessage("messages.not-enough-arguments"));
		sender.sendMessage(getMessage("messages.correct-usage-jail"));
		return true;
	    }
	    // Checks if required points exist
	    if (!pointsExist()) {
		sender.sendMessage(getMessage("messages.jail-unjail-does-not-exist"));
		return true;
	    }

	    // Gets target player name, time, until and reason
	    String apl = args[0];
	    OfflinePlayer pl = getServer().getOfflinePlayer(apl);

	    long time = getUntil(args[1]);
	    String until = getTime(time);

	    StringBuilder stb = new StringBuilder();
	    for (int i = 2; i < args.length; i++) {
		stb.append(args[i]).append(" ");
	    }
	    String reason = stb.toString();

	    // Checks if time is 0
	    if (time == 0) {
		// Sends error message
		sender.sendMessage(getMessage("messages.wrong-time-format"));
		return true;
	    }

	    // Checks if target player is online
	    if (pl.isOnline()) {
		// Jails player
		jail(pl.getPlayer(), time, reason);
	    } else {
		// Jails player offline
		jailOffline(pl, time, reason);
	    }

	    // Checks if player got jailed
	    if (isJailed(pl)) {
		// Sends success message
		sender.sendMessage(getMessage("messages.jail-success")
			// Replaces variables with data
			.replaceAll("%player%", apl).replaceAll("%reason%", reason).replaceAll("%until%", until));
	    } else {
		// Sends error message
		sender.sendMessage(getMessage("messages.jail-fail")
			// Replaces variables with data
			.replaceAll("%player%", apl));
	    }

	} else if (cmd.getName().equalsIgnoreCase("unjail")) {

	    // Checks if the number of arguments is correct
	    if (args.length > 1) {
		// Sends error message
		sender.sendMessage(getMessage("messages.too-many-arguments"));
		return true;
	    } else if (args.length < 1) {
		// Sends error message
		sender.sendMessage(getMessage("messages.not-enough-arguments"));
		return true;
	    }

	    // Gets target player
	    String apl = args[0];
	    OfflinePlayer pl = getServer().getOfflinePlayer(apl);

	    // Checks if player is jailed
	    if (isJailed(pl)) {
		// Checks if player is online
		if (pl.isOnline()) {
		    // Releases player
		    unjail(pl.getPlayer());
		} else {
		    // Releases player
		    unjailOffline(pl);
		}

		// Sends success message
		sender.sendMessage(getMessage("messages.unjail-success")
			// Replaces variables with data
			.replaceAll("%player%", apl));
	    } else {
		// Sends error message
		sender.sendMessage(getMessage("messages.unjail-fail")
			// Replaces variables with data
			.replaceAll("%player%", apl));
	    }
	} else if (cmd.getName().equalsIgnoreCase("checkjail")) {

	    // Checks if the number of arguments is correct
	    if (args.length > 1) {
		// Sends error message
		sender.sendMessage(getMessage("messages.too-many-arguments"));
		return true;
	    } else if (args.length < 1) {
		// Sends error message
		sender.sendMessage(getMessage("messages.not-enough-arguments"));
		return true;
	    }

	    // Gets player name from argument 0
	    String apl = args[0];
	    OfflinePlayer pl = getServer().getOfflinePlayer(apl);

	    // Checks if player is jailed
	    if (isJailed(pl)) {

		// Gets nicely formatted time of until they are unjailed
		String until = getTime(getConfig().getLong("players." + getName(pl) + ".releasetime"));
		// Gets player's jail reason
		String reason = getMessage("players." + getName(pl) + ".reason");

		// Sends info message
		sender.sendMessage(getMessage("messages.checkjail-is-jailed")
			// Replaces variables with data
			.replaceAll("%player%", apl).replaceAll("%until%", until).replaceAll("%reason%", reason));

	    } else {
		// Sends info message
		sender.sendMessage(getMessage("messages.checkjail-not-jailed")
			// Replaces variables with data
			.replaceAll("%player%", apl));
	    }

	} else if (cmd.getName().equalsIgnoreCase("setjail")) {

	    // Checks if the sender is not Player
	    if (!(sender instanceof Player)) {
		// Sends error message
		sender.sendMessage(getMessage("messages.console-error"));
		return true;
	    }
	    // Checks if there's too many arguments
	    if (args.length > 0) {
		// Sends error message
		sender.sendMessage(getMessage("messages.too-many-arguments"));
		return true;
	    }

	    // Gets Player from CommandSender
	    Player p = (Player) sender;

	    // Sets unjail point
	    setJailLoc(p.getLocation());

	    // Sends success message
	    p.sendMessage(getMessage("messages.setjail-success"));

	} else if (cmd.getName().equalsIgnoreCase("setunjail")) {

	    // Checks if the sender is not Player
	    if (!(sender instanceof Player)) {
		// Sends error message
		sender.sendMessage(getMessage("messages.console-error"));
		return true;
	    }
	    // Checks if there's too many arguments
	    if (args.length > 0) {
		// Sends error message
		sender.sendMessage(getMessage("messages.too-many-arguments"));
		return true;
	    }

	    // Gets Player from CommandSender
	    Player p = (Player) sender;

	    // Sets unjail point
	    setUnjailLoc(p.getLocation());

	    // Sends success message
	    p.sendMessage(getMessage("messages.setunjail-success"));

	} else if (cmd.getName().equalsIgnoreCase("simplejail")) {

	    // Reloads config file
	    reloadConfig();

	    // Sends success message
	    sender.sendMessage(getMessage("messages.reload"));

	}
	return true;
    }

    private String getName(Player p) {
	if (isUUID()) {
	    return p.getUniqueId().toString();
	} else {
	    return p.getName();
	}
    }

    private String getName(OfflinePlayer p) {
	if (isUUID()) {
	    return p.getUniqueId().toString();
	} else {
	    return p.getName();
	}
    }

    private boolean isUUID() {
	return getConfig().getBoolean("options.uuid");
    }

    /**
     * @param loc
     *            - Location of unjail point
     */
    public void setUnjailLoc(Location loc) {

	// Gets coordinates from provided location
	double x = loc.getX();
	double y = loc.getY();
	double z = loc.getZ();
	float pitch = loc.getPitch();
	float yaw = loc.getYaw();
	String world = loc.getWorld().getName();

	// Sets them into config file
	getConfig().set("points.unjail.world", world);
	getConfig().set("points.unjail.x", x);
	getConfig().set("points.unjail.y", y);
	getConfig().set("points.unjail.z", z);
	getConfig().set("points.unjail.pitch", pitch);
	getConfig().set("points.unjail.yaw", yaw);

	// Saves config file
	saveConfig();

    }

    /**
     * @param loc
     *            - Location of jail point
     */
    public void setJailLoc(Location loc) {

	// Gets coordinates from provided location
	double x = loc.getX();
	double y = loc.getY();
	double z = loc.getZ();
	float pitch = loc.getPitch();
	float yaw = loc.getYaw();
	String world = loc.getWorld().getName();

	// Sets them into config file
	getConfig().set("points.jail.world", world);
	getConfig().set("points.jail.x", x);
	getConfig().set("points.jail.y", y);
	getConfig().set("points.jail.z", z);
	getConfig().set("points.jail.pitch", pitch);
	getConfig().set("points.jail.yaw", yaw);

	// Saves config file
	saveConfig();

    }

    /**
     * @param p
     *            - Player to check jail status for
     */
    public boolean isJailed(Player p) {
	// Checks if specified player is in jailed players' list
	return getConfig().isSet("players." + getName(p));
    }

    /**
     * @param p
     *            - OfflinePlayer to check jail status for
     */
    public boolean isJailed(OfflinePlayer p) {
	// Checks if specified player is in jailed players' list
	return getConfig().isSet("players." + getName(p));
    }

    /**
     * @param time
     *            - Unix Timestamp to format
     * @return Formatted timestamp
     */
    private String getTime(long time) {

	// Gets Date from provided timestamp
	Date date = new Date(time * 1000);
	// Creates new DateFormat for our Date
	DateFormat format = new SimpleDateFormat(getMessage("options.timeformat"));
	// Sets system timezone
	format.setTimeZone(Calendar.getInstance().getTimeZone());
	// Formats our Date to readable form
	return format.format(date);

    }

    /**
     * @param p
     *            - Player to teleport to jail
     */
    public Location getJailLoc() {

	// Gets coordinates from config file
	double x = getConfig().getDouble("points.jail.x");
	double y = getConfig().getDouble("points.jail.y");
	double z = getConfig().getDouble("points.jail.z");
	float pitch = (float) getConfig().getDouble("points.jail.pitch");
	float yaw = (float) getConfig().getDouble("points.jail.yaw");
	World world = getServer().getWorld(getConfig().getString("points.jail.world"));
	// Creates new location based on coordinates
	return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * @param p
     *            - Player to unjail
     */
    public void unjail(Player p) {
	removeJailed(p);

	// Removes from config file
	getConfig().set("players." + p.getName(), null);

	// Saves config file
	saveConfig();

	// Gets coordinates from config file
	double x = getConfig().getDouble("points.unjail.x");
	double y = getConfig().getDouble("points.unjail.y");
	double z = getConfig().getDouble("points.unjail.z");
	float pitch = (float) getConfig().getDouble("points.unjail.pitch");
	float yaw = (float) getConfig().getDouble("points.unjail.yaw");
	World world = getServer().getWorld(getConfig().getString("points.unjail.world"));
	// Creates new location based on coordinates
	Location loc = new Location(world, x, y, z, yaw, pitch);

	// Teleports player to given location
	p.teleport(loc);

	// Sends info message
	p.sendMessage(getMessage("messages.you-have-been-unjailed"));

    }

    /**
     * @param p
     *            - Player name to unjail while offline
     */
    public void unjailOffline(OfflinePlayer p) {
	removeJailed(p);

	// Creates new list
	List<String> list = new ArrayList<>();

	// Checks if unjailed list already exists
	if (getConfig().isSet("unjailed")) {
	    // Gets list from config
	    list = getConfig().getStringList("unjailed");
	}

	// Adds player name to list
	list.add(getName(p));

	// Sets new list as unjailed list
	getConfig().set("unjailed", list);

	// Saves config file
	saveConfig();

    }

    /**
     * @param p
     *            - Player to check if they have been jailed offline
     * @return If player has been jailed while offline
     */
    public boolean jailedOffline(Player p) {
	// Checks if specified player is in offline jailed players' list
	return getConfig().isSet("jailed." + getName(p));

    }

    /**
     * @param p
     *            - Player to check if they have been unjailed offline
     * @return If player has been unjailed while offline
     */
    public boolean unjailedOffline(Player p) {
	// Checks if specified player is in offline unjailed players' list
	return getConfig().isSet("unjailed." + getName(p));

    }

    /**
     * @param p
     *            - Player to send to jail again (respawn or login)
     */
    private void toJailAgain(Player p) {

	// Gets player name
	String pl = getName(p);

	// Teleports them to jail
	p.teleport(getJailLoc());

	// Gets nicely formatted time of until they are unjailed
	String until = getTime(getConfig().getLong("players." + pl + ".releasetime"));
	// Gets player's jail reason
	String reason = getMessage("players." + pl + ".reason");

	// Sends info message
	p.sendMessage(getMessage("messages.still-jailed")
		// Replaces variables with data
		.replaceAll("%reason%", reason).replaceAll("%until%", until));

    }

    /**
     * @return If jail and unjail points both exist
     */
    public boolean pointsExist() {

	// Checks if jail and unjail points are set
	return getConfig().isSet("points.jail") && getConfig().isSet("points.unjail");
    }

    /**
     * @param time
     *            - Time string to calculate timestamp from
     * @return If 0, then wrong format, else timestamp
     */
    private long getUntil(String time) {

	// Checks if time string contains required letters
	if (!time.matches("(.*)(m|w|k|n|d|p|h|t)(.*)")) {
	    return 0;
	}

	// Splits time string into list based on duration
	String[] list = time.split("/(,?\\s+)|((?<=[a-z])(?=\\d))|((?<=\\d)(?=[a-z]))/i");

	// Gets current Unix Timestamp
	long until = System.currentTimeMillis() / 1000;

	// Loops through duration list
	for (String str : list) {
	    // Gets duration name and number
	    String name = str.replaceAll("\\d", "");
	    int nr = Integer.parseInt(str.replaceAll("\\D", ""));

	    // Checks for different duration names and adds to until based on
	    // duration
	    if (name.equalsIgnoreCase("months") || name.equalsIgnoreCase("month") || name.equalsIgnoreCase("mon")) {
		until += nr * 60 * 60 * 24 * 7 * 4;
	    } else if (name.equalsIgnoreCase("weeks") || name.equalsIgnoreCase("week") || name.equalsIgnoreCase("w")) {
		until += nr * 60 * 60 * 24 * 7;
	    } else if (name.equalsIgnoreCase("days") || name.equalsIgnoreCase("day") || name.equalsIgnoreCase("d")) {
		until += nr * 60 * 60 * 24;
	    } else if (name.equalsIgnoreCase("hours") || name.equalsIgnoreCase("hour") || name.equalsIgnoreCase("h")) {
		until += nr * 60 * 60;
	    } else if (name.equalsIgnoreCase("minutes") || name.equalsIgnoreCase("minute")
		    || name.equalsIgnoreCase("min") || name.equalsIgnoreCase("m")) {
		until += nr * 60;
	    }
	}

	return until;
    }

    /**
     * @param p
     *            - Player to jail
     * @param time
     *            - Unix Timestamp of release time
     * @param reason
     *            - Reason for jailing
     */
    public void jail(Player p, long time, String reason) {
	removeUnjailed(p);

	// Adds to config file
	addToConfig(getName(p), time, reason);

	// Teleports to jail
	p.teleport(getJailLoc());

	// Gets nicely formatted time of release
	String until = getTime(time);

	// Sends info message
	p.sendMessage(getMessage("messages.you-have-been-jailed")
		// Replaces variables with data
		.replaceAll("%until%", until).replaceAll("%reason%", reason));

    }

    private String getMessage(String s) {
	return ChatColor.translateAlternateColorCodes('&', getConfig().getString(s));
    }

    /**
     * @param p
     *            - Player to jail while offline
     * @param time
     *            - Unix Timestamp of release time
     * @param reason
     *            - Reason for jailing
     */
    public void jailOffline(OfflinePlayer p, long time, String reason) {
	removeUnjailed(p);

	// Adds to config file
	addToConfig(getName(p), time, reason);

	// Creates new list
	List<String> list = new ArrayList<>();

	// Checks if jailed list already exists
	if (getConfig().isSet("jailed")) {
	    // Gets list from config
	    list = getConfig().getStringList("jailed");
	}

	// Adds player name to list
	list.add(getName(p));

	// Sets new list as jailed list
	getConfig().set("jailed", list);

	// Saves config file
	saveConfig();

    }

    /**
     * @param name
     *            - Player name to add to jail
     * @param time
     *            - Unix Timestamp of release time
     * @param reason
     *            - Reason for jailing
     */
    private void addToConfig(String name, long time, String reason) {

	// Sets data into config file
	getConfig().set("players." + name + ".releasetime", time);
	getConfig().set("players." + name + ".reason", reason);

	// Saves config file
	saveConfig();

    }

    /**
     * @param player
     *            - Player name to remove from jailed list
     */
    private void removeJailed(Player p) {
	// Creates new list
	List<String> list = new ArrayList<>();

	// Checks if jailed list already exists
	if (getConfig().isSet("jailed")) {
	    // Gets list from config
	    list = getConfig().getStringList("jailed");
	}

	// Removes player name from list
	list.remove(getName(p));

	// Sets new list as jailed list
	getConfig().set("jailed", list);

	// Saves config file
	saveConfig();

    }

    /**
     * @param player
     *            - Player name to remove from jailed list
     */
    private void removeJailed(OfflinePlayer p) {
	// Creates new list
	List<String> list = new ArrayList<>();

	// Checks if jailed list already exists
	if (getConfig().isSet("jailed")) {
	    // Gets list from config
	    list = getConfig().getStringList("jailed");
	}

	// Removes player name from list
	list.remove(getName(p));

	// Sets new list as jailed list
	getConfig().set("jailed", list);

	// Saves config file
	saveConfig();

    }

    /**
     * @param player
     *            - Player name to remove from unjailed list
     */
    private void removeUnjailed(Player p) {
	// Creates new list
	List<String> list = new ArrayList<>();

	// Checks if jailed list already exists
	if (getConfig().isSet("unjailed")) {
	    // Gets list from config
	    list = getConfig().getStringList("unjailed");
	}

	// Removes player name from list
	list.remove(getName(p));

	// Sets new list as jailed list
	getConfig().set("unjailed", list);

	// Saves config file
	saveConfig();
    }

    /**
     * @param player
     *            - Player name to remove from unjailed list
     */
    private void removeUnjailed(OfflinePlayer p) {
	// Creates new list
	List<String> list = new ArrayList<>();

	// Checks if jailed list already exists
	if (getConfig().isSet("unjailed")) {
	    // Gets list from config
	    list = getConfig().getStringList("unjailed");
	}

	// Removes player name from list
	list.remove(getName(p));

	// Sets new list as jailed list
	getConfig().set("unjailed", list);

	// Saves config file
	saveConfig();
    }
}
