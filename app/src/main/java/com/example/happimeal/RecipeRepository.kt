package com.example.happimeal

object RecipeRepository {

    // Basic staples (salt, pepper, oil, water) are assumed available and are not listed as ingredients.
    private fun r(name: String, ingredients: String, steps: String) =
        Recipe(name, ingredients.split(",").map { it.trim() }, steps)

    val recipes: List<Recipe> = listOf(
        r("Omelette", "egg, onion, tomato",
            "1. Beat eggs with salt and pepper.\n2. Saute chopped onion and tomato in oil.\n3. Pour in eggs, cook until set, fold and serve."),
        r("Fried Rice", "rice, egg, carrot, onion",
            "1. Saute onion and diced carrot.\n2. Push aside, scramble the egg.\n3. Add cooked rice, season with salt/soy sauce, stir-fry 3-4 min."),
        r("Chicken Adobo", "chicken, garlic, onion, soy sauce, vinegar",
            "1. Simmer chicken with soy sauce, vinegar, garlic, onion and pepper for 30-40 min.\n2. Reduce sauce and serve with rice."),
        r("Tomato Pasta", "pasta, tomato, garlic, onion",
            "1. Boil pasta.\n2. Saute garlic and onion, add chopped tomato and simmer 10 min.\n3. Toss with pasta."),
        r("Scrambled Eggs on Toast", "egg, bread, butter",
            "1. Toast the bread.\n2. Scramble eggs in butter on low heat.\n3. Serve on toast."),
        r("Garlic Fried Rice", "rice, garlic, egg",
            "1. Fry minced garlic until golden.\n2. Add cooked rice, salt and stir-fry.\n3. Serve with fried egg."),
        r("Potato Soup", "potato, onion, garlic, milk",
            "1. Saute onion and garlic.\n2. Add diced potato and water, boil until soft.\n3. Mash partly, stir in milk, season."),
        r("Banana Pancakes", "banana, egg, flour, milk",
            "1. Mash banana, mix with egg, flour and milk.\n2. Cook spoonfuls on a greased pan until golden on both sides."),
        r("Chicken Stir-Fry", "chicken, carrot, onion, garlic, soy sauce",
            "1. Stir-fry sliced chicken until cooked.\n2. Add garlic, onion, carrot.\n3. Splash soy sauce, cook 3 min."),
        r("Cheese Sandwich", "bread, cheese, butter",
            "1. Butter the bread, add cheese.\n2. Grill in a pan until golden and melted."),
        r("Vegetable Soup", "carrot, potato, onion, cabbage",
            "1. Saute onion.\n2. Add chopped carrot, potato, cabbage and water.\n3. Simmer 20 min, season."),
        r("Tuna Pasta", "pasta, tuna, garlic, onion",
            "1. Boil pasta.\n2. Saute garlic and onion, add drained tuna.\n3. Toss with pasta and pepper."),
        r("Egg Fried Noodles", "noodles, egg, carrot, cabbage",
            "1. Boil noodles and drain.\n2. Stir-fry vegetables, push aside, scramble egg.\n3. Add noodles and soy sauce, toss."),
        r("Mashed Potatoes", "potato, butter, milk",
            "1. Boil potatoes until soft.\n2. Mash with butter and milk, season with salt."),
        r("Tomato Egg Stir-Fry", "tomato, egg, garlic",
            "1. Scramble eggs and set aside.\n2. Saute garlic and tomato until saucy.\n3. Return eggs, mix, season."),
        r("Fruit Salad", "banana, apple, orange",
            "1. Chop all fruit.\n2. Mix and chill before serving."),
        r("Beef Stew", "beef, potato, carrot, onion, tomato",
            "1. Brown beef cubes.\n2. Add onion, tomato, carrot, potato and water.\n3. Simmer 1.5-2 hours until tender."),
        r("Fish with Tomato", "fish, tomato, onion, garlic",
            "1. Saute garlic, onion, tomato.\n2. Add fish and a little water, simmer 10 min."),
        r("Cabbage Stir-Fry", "cabbage, garlic, carrot",
            "1. Fry garlic.\n2. Add sliced carrot then cabbage.\n3. Stir-fry 4-5 min, season."),
        r("French Toast", "bread, egg, milk",
            "1. Whisk egg with milk.\n2. Dip bread slices and fry in butter/oil until golden.")
    )

    fun normalize(s: String): String {
        val t = s.trim().lowercase()
        return when {
            t.endsWith("ies") -> t.dropLast(3) + "y"
            t.endsWith("oes") -> t.dropLast(2)
            t.endsWith("s") && !t.endsWith("ss") -> t.dropLast(1)
            else -> t
        }
    }

    /** Every ingredient name the app knows about (used to filter photo-detected labels). */
    val knownIngredients: Set<String> =
        recipes.flatMap { it.ingredients }.map { normalize(it) }.toSet()

    fun suggest(userIngredients: List<String>): List<Match> {
        val have = userIngredients.map { normalize(it) }.toSet()
        return recipes.mapNotNull { rec ->
            val got = rec.ingredients.filter { normalize(it) in have }
            if (got.isEmpty()) null
            else Match(rec, got, rec.ingredients.filter { it !in got })
        }.sortedWith(compareByDescending<Match> { it.ratio }.thenBy { it.missing.size })
    }
}