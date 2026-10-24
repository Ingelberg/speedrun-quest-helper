QUEST = "Ernest the Chicken"
PROGRESS = {"varp": 32}
STEPS = [
    S(0, qh="talkToVeronica", progress=[0]),
    S(1, qh="enterManor", progress=[1]),
    S(2, qh="pickupFishFood", ground=["Fish food"], via=[T(3109, 3364, 0)], objectIds=[11498], done={"have": "Fish food"}),
    S(3, qh="pickupPoison", ground=["Poison"], via=[T(3109, 3365, 1)], objectIds=[11499], done={"have": "Poison"}),
    S(4, use=["Fish food", "Poison"], done={"have": "Poisoned fish food"}),
    S(5, qh="searchBookcase"),
    S(6, qh="goDownLadder", ground=["Oil can"], done={"have": "Oil can"}),
    S(7, qh=["goUpFromBasement", "pullLeverToLeave"]),
    S(8, qh="pickupSpade", ground=["Spade"], done={"have": "Spade"}),
    S(10, target=T(3085, 3361, 0, "Compost heap"), objectIds=[152]),
    S(11, qh="searchCompost", done={"have": "Key"}),
    S(13, qh="useFishFoodOnFountain", use=["Poisoned fish food"]),
    S(14, qh="searchFountain", done={"have": "Pressure gauge"}),
    S(15, qh="getTube", ground=["Rubber tube"], done={"have": "Rubber tube"}),
    S(16, target=T(3116, 3364, 2, "Professor Oddenstein"), via=[T(3109, 3364, 0), T(3105, 3363, 1)], objectIds=[11498, 11511]),
    S(17, qh="talkToOddenstein"),
    S(18, qh="talkToOddenteinAgain", progress=[2], use=["Pressure gauge", "Rubber tube", "Oil can"]),
]
