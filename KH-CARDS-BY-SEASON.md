# King's Hand — Cards by Season (combat / deckbuilder reference)

Authoritative mapping of which cards belong to which season, taken from
`CardCatalog.defaultCatalog()`. This will matter for the season-gated draft pools and for
unlock timing (GOLD via TREASURY, STRENGTH via BARRACKS) as the roguelike/deckbuilder format
comes together. Keep this in sync when cards move seasons.

Legend: `[econ]` production/food/pop economy · `[gold]` needs GOLD unlocked (TREASURY) ·
`[str]` needs STRENGTH unlocked (BARRACKS) · `[sabotage]` drains a rival resource ·
`[damage]` **new** — direct population attack (SINGLE-use, shielded by rival STRENGTH) ·
`[great work]` multi-season construction.

## Spring 🌱
- FARM, SEEDS, FERTILIZER, MINE, HUNTING_CAMP, SETTLERS, LUMBER_YARD, COMMUNITY_WELL, IRRIGATION `[econ]`
- MARKETPLACE `[gold]`
- TRAINING_GROUNDS `[str]`
- **RAIDERS_STRIKE `[damage]` — POPULATION −10, cost 120**
- THE_GREAT_RESERVOIR_CONSTRUCTION `[great work]` (no gold/strength cost — always available)

## Summer ☀️
- BUNKHOUSE, MANOR, FARMSTEAD, THE_HUNT, LOGGING, TRADING_POST, FISHING_VILLAGE, SCHOOL_HOUSE,
  EARLY_HARVEST, WINDMILL, CARAVAN, TOWN_FAIR, STONE_QUARRY, MERCHANT_GUILD `[econ]`
- TRADE_ROUTES `[gold]`
- GUARD_POST `[str]`
- SABOTEURS `[sabotage][str]` (drains rival PRODUCTION)
- **WARBAND `[damage]` — POPULATION −16, cost 220**
- THE_GREAT_ROAD_CONSTRUCTION `[great work][gold]`

## Fall 🍂
- WORKSHOP, HARVEST, TEXTILES, BAKERY, TAX_COLLECTION, STOREHOUSE, BARN, MASON_GUILD,
  METALWORKING, BLACKSMITH `[econ]`
- GOLD_MINE `[gold]`
- ARMORY `[str]`
- CROP_RAID `[sabotage][str]` (burns rival FOOD)
- **SIEGE_ENGINES `[damage]` — POPULATION −24, cost 340**
- THE_GREAT_CAPITAL_CONSTRUCTION `[great work][gold+str]`

## Winter ❄️
- FORGE, WOODPILES, GRANARY, EMERGENCY_RATIONS, STOCKPILE, CHAPEL, PRAY, DESPERATE_TIMES `[econ]`
- FOREIGN_AID `[gold]`
- FORTRESS_WALL `[str]`
- WINTER_RAIDERS `[sabotage][str]` (drains rival PRODUCTION + FOOD)
- **ONSLAUGHT `[damage]` — POPULATION −34, cost 480**
- THE_GREAT_CONQUEST_CONSTRUCTION `[great work][gold+str]`

## Great Works (commission season)
- TREASURY → commissioned in **Summer**; completes → unlocks **GOLD**
- BARRACKS → commissioned in **Fall**; completes → unlocks **STRENGTH** (also the combat "shield" resource)
- THE_GREAT_RESERVOIR → Spring · THE_GREAT_ROAD → Summer · THE_GREAT_CAPITAL → Fall ·
  THE_GREAT_CONQUEST → Winter (the four win-condition works)

## Notes for the combat format
- **STRENGTH is now dual-purpose:** its own economy resource *and* the defensive shield that absorbs
  incoming `[damage]` before population is hit (see `GameEngine.applyIncomingDelta`). So BARRACKS
  (which unlocks STRENGTH) is the gate to having any defense — until then, damage cards land directly
  on population.
- Damage-card magnitudes/costs above are first-pass, tuned against a 100-population kingdom; expect
  to rebalance once the turn loop and draft economy are in.
- Seasons act as the run's "acts": these lists are the natural per-act draft pools.
