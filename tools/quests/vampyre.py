QUEST = "Vampyre Slayer"
PROGRESS = {"varp": 178}
STEPS = [
    S(0, qh="getGarlic", via=[T(3100, 3267, 0)], objectIds=[2613, 15645]),
    S(2, qh="talkToMorgan", progress=[0]),
    S(3, qh="getGarlic", progress=[1], via=[T(3100, 3267, 0)], objectIds=[2613, 15645], done={"have": "Garlic"}),
    S(4, npc="Diango", shop=["Chronicle", "Teleport card"], done={"have": "Teleport card"}),
    S(5, use=["Chronicle"]),
    S(6, qh="buyBeer", npcs=["Bartender"], target=T(3226, 3399, 0, "Bartender"), done={"have": "Beer"}),
    S(7, qh="talkToHarlow"),
    S(8, qh="talkToHarlowAgain", progress=[2], done={"have": "Stake"}),
    S(9, npc="Ranged combat tutor", widget=14286854, pathWithin=40, done={"have": "Training bow"}),
    S(10, npcs=["Shop keeper", "Shop assistant"], shop=["Hammer"], target=T(3211, 3247, 0, "General store"), done={"have": "Hammer"}),
    S(11, qh="goDownToBasement"),
    S(12, qh="goDownToBasement"),
    S(13, qh=["killDraynor", "openCoffin"]),
]
