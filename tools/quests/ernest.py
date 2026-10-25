QUEST = "Ernest the Chicken"
PROGRESS = {"varp": 32}

# Lever puzzle in the basement: Quest Helper's order, ids (LEVERA-F) and varbits (ERNESTLEVER_A-F, 1 = down)
LEVERS = {
    "A": (T(3108, 9745, 0, "Lever A"), 146, 1788),
    "B": (T(3118, 9752, 0, "Lever B"), 147, 1789),
    "C": (T(3112, 9760, 0, "Lever C"), 148, 1790),
    "D": (T(3108, 9767, 0, "Lever D"), 149, 1791),
    "E": (T(3097, 9767, 0, "Lever E"), 150, 1792),
    "F": (T(3096, 9765, 0, "Lever F"), 151, 1793),
}


def lever(name, down, text=None, **extra):
    target, object_id, varbit = LEVERS[name]
    part = {"text": text or f"Pull {'down' if down else 'up'} lever {name}.", "target": target,
            "objectIds": [object_id], "done": {"varbit": varbit, "is": 1 if down else 0}}
    part.update(extra)
    return part


# Areas from Quest Helper's zones, as x1, y1, x2, y2, plane
SECRET_ROOM = [3090, 3354, 3096, 3363, 0]
BASEMENT = [3090, 9745, 3118, 9767, 0]
MANOR_GROUND = [3097, 3354, 3119, 3373, 0]

STEPS = [
    S(0, qh="talkToVeronica", progress=[0]),
    S(1, qh="enterManor", progress=[1], target=T(3108, 3353, 0, "Door")),
    S(2, qh="pickupFishFood", ground=["Fish food"], target=T(3108, 3356, 1, "Fish food"), via=[T(3109, 3364, 0)],
      objectIds=[11498], done={"have": "Fish food"}),
    S(3, qh="pickupPoison", ground=["Poison"], target=T(3097, 3366, 0, "Poison"), via=[T(3109, 3365, 1)],
      objectIds=[11499], done={"have": "Poison"}),
    S(4, use=["Fish food", "Poison"], done={"have": "Poisoned fish food"}),
    S(5, qh="searchBookcase", done={"inArea": [SECRET_ROOM, BASEMENT]}),
    S(6, split=[
        lever("A", True, "Climb down the ladder and pull down lever A.", via=[T(3092, 3362, 0)], objectIds=[133, 146]),
        lever("B", True),
        lever("D", True),
        lever("B", False),
        lever("A", False),
        lever("F", True),
        lever("E", True),
        lever("C", True),
        lever("E", False),
        {"text": "Pick up the oil can in the west room.", "target": T(3092, 9755, 0, "Oil can"), "ground": ["Oil can"],
         "done": {"have": "Oil can"}},
    ]),
    S(7, target=T(3096, 3357, 0, "Lever"), via=[T(3117, 9754, 0)], objectIds=[132, 160], done={"inArea": [MANOR_GROUND]}),
    S(8, qh="pickupSpade", ground=["Spade"], target=T(3120, 3359, 0, "Spade"), done={"have": "Spade"}),
    S(10, target=T(3085, 3361, 0, "Compost heap"), objectIds=[152]),
    S(11, qh="searchCompost", done={"have": "Key"}),
    S(13, qh="useFishFoodOnFountain", use=["Poisoned fish food"]),
    S(14, qh="searchFountain", done={"have": "Pressure gauge"}),
    S(15, qh="getTube", ground=["Rubber tube"], target=T(3111, 3367, 0, "Rubber tube"), done={"have": "Rubber tube"}),
    S(16, target=T(3116, 3364, 2, "Professor Oddenstein"), via=[T(3109, 3364, 0), T(3105, 3363, 1)], objectIds=[11498, 11511]),
    S(17, qh="talkToOddenstein"),
    S(18, qh="talkToOddenteinAgain", progress=[2], use=["Pressure gauge", "Rubber tube", "Oil can"]),
]
