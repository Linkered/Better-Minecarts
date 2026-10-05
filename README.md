![Better Minecarts](https://raw.githubusercontent.com/Linkered/Better-Minecarts/main/.github/assets/banner.png)

# Better Minecarts

> **Minecart Improvements made minecarts smooth, fast and fun again… so why can't they pull each other yet?**
> The chains were in your inventory all along.

**Better Minecarts** turns loose minecarts into real trains: link them with a chain, put a Furnace Minecart in front
and let it pull. Everything is made from vanilla pieces, so it feels like something that shipped with the game.

> [!IMPORTANT]
> Trains only work in worlds with the **Minecart Improvements** experiment. The mod turns it on for you in every new
> world. Worlds created before installing the mod still load fine, but their minecarts can't be linked.

## Getting started

1. Place two minecarts on a track.
2. Use a **chain** on one minecart, then on the other, just like a lead.
3. Link a **Furnace Minecart** to the front and give it coal.
4. Watch your train go!

## Features

### Linking minecarts

- **Link with a chain:** use a chain on a minecart, then on another one. Use it on the first minecart again to change
  your mind. Iron and copper chains both work, and they look like themselves.
- **Unlink with shears:** use shears on a minecart to cut the chain closest to where you click. You get the chain back.
- **Breaks like a real chain:** if linked minecarts end up too far apart, or one of them breaks, the chain snaps and
  drops.

### Real trains

- **Moves as one:** every minecart in a train goes at the same speed and keeps the same distance, through slopes,
  tight curves and zigzags. Minecarts in a train never bump into each other.
- **Furnace Minecarts pull:** one Furnace Minecart pulls itself and four empty minecarts at full speed on flat track.
  Longer or heavier trains (a Minecart with Chest full of items is heavy) go slower, and adding more Furnace Minecarts
  makes them faster. A Furnace Minecart on its own works exactly like in vanilla.
- **Keeps going when you're away:** a burning Furnace Minecart keeps the world around its train loaded, like a thrown
  Ender Pearl does, so trains don't stop when you leave them behind. Once it runs out of fuel, it stops doing so.

### Fuel and automation

- **Tender:** a Minecart with Hopper linked right behind or in front of a burning Furnace Minecart feeds it fuel
  whenever it has room. It never relights one that has gone out.
- **Block of Coal:** Furnace Minecarts can burn Blocks of Coal, which last as long as 8 pieces of coal.
- **Dispensers:**
  - with fuel, they refuel the Furnace Minecart in front of them;
  - with a chain, they link the minecart in front of them to the nearest one;
  - with shears, they cut the chain closest to them.

  When there is nothing to refuel, link or cut, the item works as it always did.
- **Activator Rail:** a powered Activator Rail pauses a Furnace Minecart (it goes out but keeps its fuel), and an
  unpowered one lets it carry on, the same way it turns Minecarts with Hopper off and on.
- **Detector Rail:** a Comparator shows how much fuel a Furnace Minecart on the rail has left, the same way it shows
  how full a Minecart with Chest is.

### Fixes

- Furnace Minecarts no longer get stuck for good on tight zigzag curves.

## FAQ

<details>
<summary><b>Why aren't Furnace Minecarts faster?</b></summary>

<br>

They keep their vanilla top speed, which is half as fast as a minecart on Powered Rails. Furnace Minecarts are the
slow, cheap way to move a train; Powered Rails are still the fast one. If you want faster trains, raise the
minecart speed game rule.

</details>

<details>
<summary><b>Will it slow down my game or server?</b></summary>

<br>

No. A train costs about the same as the same number of loose minecarts, and keeping the world around it loaded costs
about as much as a thrown Ender Pearl. That only happens while the Furnace Minecart is burning.

</details>

<details>
<summary><b>Can data packs change which items link minecarts?</b></summary>

<br>

Yes. The items that link minecarts are the `#better-minecarts:minecart_couplers` item tag (all chains by default),
and Blocks of Coal are added to the `#minecraft:furnace_minecart_fuel` tag.

</details>

## Installation

- Requires [Fabric API](https://modrinth.com/mod/fabric-api).
- Needed on both the client and the server.
