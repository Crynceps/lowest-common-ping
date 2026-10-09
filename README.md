# Lowest Common Ping

Find the Old School RuneScape world with the lowest ping for **everyone** in your RuneLite party. Like a lowest
common denominator, it looks for the world where the member with the highest ping is still as low as possible.

Every party member running the plugin pings the worlds from their own connection and shares the results over the
RuneLite party. The side panel lists the worlds sorted by the best ping for the whole group, with a column for each
member, so a party spread across countries (say Europe and Australia) can see which world suits everyone.

## Usage

1. Everyone installs **Lowest Common Ping**.
2. Create or join a party with RuneLite's built-in **Party** plugin (same passphrase for everyone).
3. Open the **Lowest Common Ping** side panel (signal bars icon).

The panel shows:

- **World**: world number and region (members worlds in yellow, your current world in green).
- **Worst / Avg**: the party's score for that world, depending on *Rank worlds by*.
- One column per member (up to four, "You" first) with that member's ping.

Hover over a row for details: activity, player count, each member's ping and how your own value was measured.
Click a column header to sort by it. Double-click a world (or right-click > *Hop to world*) to hop there.

Values: `-` not measured yet, `t/o` the world did not answer. Worlds where a member has no data are listed after
worlds everyone has measured, and worlds a member cannot reach come after those.

Without a party the panel still works and shows only your own pings.

### Ranking

- **Worst member** (default): the world where the slowest member has the lowest ping. This is the fairest choice
  for a group, since nobody ends up with a bad connection.
- **Average**: the lowest ping averaged over all members.

Worlds with equal pings (common for worlds in the same data center) are ordered by player count, so the suggestion
leans towards quieter worlds. Full worlds are never suggested as the best world.

Your own value for a world is the median of the pings measured within the *Averaging window* (default 30 seconds),
so a single lag spike does not throw it off. If a world has not been pinged within the window, the median of its
last few measurements is used (shown as "Xs ago" in the tooltip).

### Overlay

While at least one other party member shares pings, a small overlay lists the best worlds for the party. You can turn
it off under *Display > Show overlay*.

### Hopping

Hopping only happens when you double-click a world or use its right-click menu. On the login screen the world is
selected directly. In game, the plugin opens the world switcher and hops the same way the core World Hopper plugin
does. PvP, high risk and other special worlds are never listed, so you cannot hop to them from this plugin.
The hop code is adapted from RuneLite's World Hopper plugin (BSD-2, see the notice in `WorldHopper.java`).

## How it measures

The plugin uses RuneLite's own world ping (the one the World Hopper uses): ICMP, falling back to a TCP connect for
worlds and networks that block ICMP. It does not open any connections of its own.

Pinging every world every few seconds would be wasteful, so pings are spread over two lanes:

- The **8 best worlds** for the party are pinged every 5 seconds, so their values stay accurate. A world stays in
  this group while it ranks in the top 12.
- **All other worlds** are pinged about every 3 minutes, which is enough to notice when another world becomes better.
- If the ping rate is too low for both (for example 1 ping per second with every region selected, or on a network
  where every ping needs the slower TCP fallback), the other worlds take over as soon as they fall behind, so no
  value expires. They are then pinged about every 6 minutes, and the best worlds about every 30 seconds.
- A world that does not answer is retried later and later (30 seconds, doubling up to 6 minutes).

At most *Max pings per second* (default 3) pings are started per second while the side panel is open, and at most
2 per second while it is closed. Pinging only runs while the side panel is open, or while another party member uses
the plugin (you can turn the latter off). For comparison, the core World Hopper pings every world once at startup
and then one world every 3 seconds.

Use the *Worlds* settings (members/free, regions, skill total worlds) to limit which worlds are pinged and listed.
PvP, high risk, Deadman, seasonal, beta, speedrunning, tournament and other special worlds are never included.

## Party traffic

Updates are kept small to go easy on RuneLite's party server:

- About 4 bytes per world; a full update of 300 worlds is about 1.6 KB.
- A full update is sent when joining a party, when another member asks for one, and every 2 minutes.
- In between, only worlds whose ping changed noticeably are sent, at most every 10 seconds.
- Nothing is sent while you are alone in the party, and nothing periodic unless another member uses the plugin.
- Requests for full updates are rate limited per member.
- In parties of 8 or more, all intervals are stretched, like the core Party plugin does.

## Privacy

Sharing pings with your party reveals roughly where you are, since ping depends on distance to each world. Only
members of your party receive them. Your in-game name is included so members can tell the columns apart.

Turn off *Pinging > Share pings with party* to only watch: you still see the pings of members who share, and they
only see your name and that you use the plugin.

## Development

Requires a JDK 11 to 21.

```bash
./gradlew build
```

```bash
./gradlew run
```

`run` starts RuneLite in developer mode with the plugin loaded. To log in with a Jagex account, follow
[Using Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts).
