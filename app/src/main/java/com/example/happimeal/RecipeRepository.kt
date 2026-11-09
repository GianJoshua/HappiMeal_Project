package com.example.happimeal

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class MealSearchResponse(val meals: List<MealSummary>?)
data class MealSummary(val idMeal: String, val strMeal: String, val strMealThumb: String?)

data class MealDetailResponse(val meals: List<MealDetail>?)
data class MealDetail(
    val idMeal: String,
    val strMeal: String,
    val strInstructions: String?,
    val strIngredient1: String?, val strIngredient2: String?, val strIngredient3: String?,
    val strIngredient4: String?, val strIngredient5: String?, val strIngredient6: String?,
    val strIngredient7: String?, val strIngredient8: String?, val strIngredient9: String?,
    val strIngredient10: String?, val strIngredient11: String?, val strIngredient12: String?,
    val strIngredient13: String?, val strIngredient14: String?, val strIngredient15: String?,
    val strIngredient16: String?, val strIngredient17: String?, val strIngredient18: String?,
    val strIngredient19: String?, val strIngredient20: String?
) {
    fun toRecipe(): Recipe {
        val ingList = listOfNotNull(
            strIngredient1, strIngredient2, strIngredient3, strIngredient4, strIngredient5,
            strIngredient6, strIngredient7, strIngredient8, strIngredient9, strIngredient10,
            strIngredient11, strIngredient12, strIngredient13, strIngredient14, strIngredient15,
            strIngredient16, strIngredient17, strIngredient18, strIngredient19, strIngredient20
        ).map { it.trim() }.filter { it.isNotBlank() }

        val cleanSteps = strInstructions?.trim() ?: "No instructions provided."
        return Recipe(name = strMeal, ingredients = ingList, steps = cleanSteps)
    }
}

object RecipeRepository {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val llmRecipesCache = mutableListOf<Recipe>()

    // freellmapi configuration (OpenAI-compatible router for free LLM providers)
    private var llmBaseUrl = "http://10.0.2.2:3001/v1"
    private var llmApiKey = "freellmapi-key"

    fun setLlmConfig(baseUrl: String, apiKey: String) {
        if (baseUrl.isNotBlank()) llmBaseUrl = baseUrl.trim().removeSuffix("/")
        if (apiKey.isNotBlank()) llmApiKey = apiKey.trim()
    }

    // Basic staples (salt, pepper, oil, water) are assumed available and are not listed as ingredients.
    private fun r(name: String, ingredients: String, steps: String, time: String = "30 Mins", difficulty: String = "Medium") =
        Recipe(name, ingredients.split(",").map { it.trim() }, steps, time, difficulty)

    val recipes: List<Recipe> = listOf(
        r("Pork Adobo", "pork, garlic, onion, soy sauce, vinegar",
            "1. Simmer pork with soy sauce, vinegar, garlic, onion and pepper until tender.\n2. Reduce sauce until glossy and serve hot with white rice."),
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
            "1. Brown beef cubes.\n2. Add onion, tomato, carrot, potato and water.\n3. Simmer 1.5-2 hours until tender.", "2 Hours", "Medium"),
        r("Fish with Tomato", "fish, tomato, onion, garlic",
            "1. Saute garlic, onion, tomato.\n2. Add fish and a little water, simmer 10 min.", "15 Mins", "Easy"),
        r("Cabbage Stir-Fry", "cabbage, garlic, carrot",
            "1. Fry garlic.\n2. Add sliced carrot then cabbage.\n3. Stir-fry 4-5 min, season.", "10 Mins", "Easy"),
        r("French Toast", "bread, egg, milk",
            "1. Whisk egg with milk.\n2. Dip bread slices and fry in butter/oil until golden.", "10 Mins", "Easy"),
        // NEW EXTENSIVE RECIPES
        r("Spaghetti Bolognese", "pasta, ground beef, onion, garlic, tomato sauce, carrot",
            "1. Saute onion, garlic, and carrot.\n2. Brown ground beef.\n3. Add tomato sauce and simmer.\n4. Serve over boiled pasta.", "40 Mins", "Medium"),
        r("Chicken Curry", "chicken, curry powder, coconut milk, onion, garlic, potato, carrot",
            "1. Saute onion and garlic.\n2. Add chicken and curry powder.\n3. Stir in coconut milk, potatoes, and carrots. Simmer until tender.", "45 Mins", "Medium"),
        r("Pork Chops with Apple Sauce", "pork chop, apple, butter, cinnamon",
            "1. Pan-fry pork chops until cooked through.\n2. Saute sliced apples in butter and cinnamon until soft.\n3. Serve together.", "25 Mins", "Easy"),
        r("Avocado Toast", "bread, avocado, lemon, egg",
            "1. Toast bread.\n2. Mash avocado with lemon juice.\n3. Spread on toast and top with a fried egg.", "10 Mins", "Easy"),
        r("Shrimp Fried Rice", "rice, shrimp, egg, peas, carrot, soy sauce, garlic",
            "1. Scramble egg and set aside.\n2. Stir-fry shrimp and garlic until pink.\n3. Add veggies, rice, egg, and soy sauce. Toss well.", "20 Mins", "Medium"),
        r("Caprese Salad", "tomato, mozzarella, basil, balsamic vinegar, olive oil",
            "1. Slice tomatoes and mozzarella.\n2. Layer on a plate with basil leaves.\n3. Drizzle with balsamic vinegar and olive oil.", "5 Mins", "Easy"),
        r("Lentil Soup", "lentil, carrot, celery, onion, garlic, vegetable broth",
            "1. Saute onion, garlic, carrot, and celery.\n2. Add lentils and broth.\n3. Simmer for 30 minutes until lentils are soft.", "40 Mins", "Medium"),
        r("Beef Tacos", "ground beef, taco shell, cheese, lettuce, tomato, taco seasoning",
            "1. Brown ground beef with taco seasoning.\n2. Fill taco shells with beef, cheese, lettuce, and diced tomato.", "20 Mins", "Easy"),
        r("Chicken Caesar Salad", "chicken, romaine lettuce, parmesan cheese, croutons, caesar dressing",
            "1. Grill and slice chicken.\n2. Toss lettuce, parmesan, croutons, and dressing.\n3. Top with chicken.", "15 Mins", "Easy"),
        r("Mushroom Risotto", "arborio rice, mushroom, onion, garlic, parmesan cheese, chicken broth, butter",
            "1. Saute onion, garlic, and mushrooms in butter.\n2. Add rice and gradually stir in warm broth until creamy.\n3. Finish with parmesan.", "45 Mins", "Hard"),
        r("Pancakes", "flour, milk, egg, butter, baking powder, maple syrup",
            "1. Whisk flour, milk, egg, and baking powder.\n2. Cook batter on a buttered griddle.\n3. Serve with maple syrup.", "20 Mins", "Easy"),
        r("Teriyaki Chicken", "chicken, soy sauce, brown sugar, ginger, garlic, rice",
            "1. Mix soy sauce, sugar, ginger, and garlic for sauce.\n2. Pan-fry chicken and glaze with sauce.\n3. Serve with cooked rice.", "25 Mins", "Medium"),
        r("Greek Salad", "cucumber, tomato, red onion, feta cheese, olives, olive oil, lemon",
            "1. Chop cucumber, tomato, and onion.\n2. Mix with feta and olives.\n3. Dress with olive oil and lemon juice.", "10 Mins", "Easy"),
        r("Baked Salmon", "salmon, lemon, garlic, butter, asparagus",
            "1. Place salmon and asparagus on a baking sheet.\n2. Top with garlic butter and lemon slices.\n3. Bake at 400°F (200°C) for 15 minutes.", "25 Mins", "Easy"),
        r("Vegetable Curry", "cauliflower, potato, green beans, coconut milk, curry paste, onion",
            "1. Saute onion and curry paste.\n2. Add veggies and coconut milk.\n3. Simmer until vegetables are tender.", "35 Mins", "Medium"),
        r("Quesadillas", "tortilla, cheese, chicken, bell pepper, onion",
            "1. Saute sliced bell pepper and onion.\n2. Place cheese, chicken, and veggies between two tortillas.\n3. Grill until cheese melts.", "15 Mins", "Easy"),
        r("Minestrone Soup", "pasta, kidney beans, tomato, carrot, celery, zucchini, vegetable broth",
            "1. Chop all vegetables.\n2. Simmer veggies and beans in broth for 20 minutes.\n3. Add pasta and cook until tender.", "45 Mins", "Medium"),
        r("Guacamole", "avocado, onion, tomato, jalapeno, cilantro, lime",
            "1. Mash avocados.\n2. Mix in finely chopped onion, tomato, jalapeno, and cilantro.\n3. Stir in lime juice and salt.", "10 Mins", "Easy"),
        r("Chicken Fajitas", "chicken, bell pepper, onion, tortilla, fajita seasoning",
            "1. Slice chicken, peppers, and onions.\n2. Stir-fry with fajita seasoning.\n3. Serve in warm tortillas.", "20 Mins", "Medium"),
        r("Pesto Pasta", "pasta, pesto sauce, cherry tomato, parmesan cheese",
            "1. Boil pasta and drain.\n2. Toss with pesto sauce and halved cherry tomatoes.\n3. Top with parmesan.", "15 Mins", "Easy"),
        r("Smoothie Bowl", "banana, frozen berries, milk, granola, honey",
            "1. Blend banana, berries, and a splash of milk until thick.\n2. Pour into a bowl and top with granola and a drizzle of honey.", "10 Mins", "Easy"),
        r("Stuffed Bell Peppers", "bell pepper, ground turkey, rice, tomato sauce, cheese",
            "1. Halve and clean bell peppers.\n2. Mix cooked ground turkey, rice, and tomato sauce.\n3. Stuff peppers, top with cheese, and bake at 375°F (190°C) for 30 mins.", "45 Mins", "Medium"),
        r("Macaroni and Cheese", "macaroni, cheddar cheese, milk, butter, flour",
            "1. Boil macaroni.\n2. Make a roux with butter and flour, whisk in milk and cheese until smooth.\n3. Mix with pasta.", "30 Mins", "Medium"),
        r("Pork Fried Rice", "rice, pork, egg, peas, soy sauce, sesame oil",
            "1. Scramble egg and set aside.\n2. Stir-fry diced pork until browned.\n3. Add peas, rice, egg, soy sauce, and sesame oil. Toss well.", "20 Mins", "Medium"),
        r("Lemon Herb Chicken", "chicken breast, lemon, thyme, garlic, olive oil",
            "1. Marinate chicken in lemon juice, thyme, garlic, and oil.\n2. Bake or grill until cooked through.", "25 Mins", "Easy"),
        r("Egg Salad Sandwich", "egg, mayonnaise, mustard, bread, lettuce",
            "1. Hard-boil eggs and chop.\n2. Mix with mayo and a dash of mustard.\n3. Serve on bread with lettuce.", "15 Mins", "Easy"),
        r("Chili Con Carne", "ground beef, kidney beans, tomato sauce, chili powder, onion, garlic",
            "1. Brown beef with onion and garlic.\n2. Add beans, tomato sauce, and chili powder.\n3. Simmer for 30 minutes.", "45 Mins", "Medium"),
        r("Cauliflower Fried Rice", "cauliflower, egg, carrot, peas, soy sauce, green onion",
            "1. Grate cauliflower into 'rice'.\n2. Scramble egg and set aside.\n3. Stir-fry veggies and cauliflower, add egg and soy sauce.", "15 Mins", "Medium"),
        r("Tofu Stir-Fry", "tofu, broccoli, bell pepper, soy sauce, ginger, garlic",
            "1. Press and cube tofu, then pan-fry until crispy.\n2. Stir-fry broccoli and bell pepper with ginger and garlic.\n3. Combine with tofu and soy sauce.", "20 Mins", "Medium"),
        r("BLT Sandwich", "bacon, lettuce, tomato, bread, mayonnaise",
            "1. Fry bacon until crispy.\n2. Toast bread and spread with mayo.\n3. Assemble sandwich with bacon, lettuce, and sliced tomato.", "15 Mins", "Easy"),
        r("Shepherd's Pie", "ground lamb, potato, peas, carrot, onion, gravy",
            "1. Cook ground lamb with onion, peas, and carrots. Mix with gravy.\n2. Spread in a baking dish and top with mashed potatoes.\n3. Bake until golden.", "1 Hour", "Hard"),
        r("Sausage and Mash", "sausage, potato, onion, gravy, butter, milk",
            "1. Boil and mash potatoes with butter and milk.\n2. Fry sausages and slice onions to make onion gravy.\n3. Serve sausages over mash with gravy.", "35 Mins", "Medium"),
        r("Fish Tacos", "white fish, tortilla, cabbage, lime, sour cream, cilantro",
            "1. Pan-fry or bake fish with spices.\n2. Shred cabbage and mix with sour cream and lime.\n3. Assemble in tortillas and top with cilantro.", "20 Mins", "Medium"),
        // FILIPINO RECIPES
        r("Sinigang na Baboy", "pork, tamarind, tomato, onion, water spinach (kangkong), radish",
            "1. Boil pork with onion and tomato until tender.\n2. Add tamarind paste/powder for sourness.\n3. Add radish and kangkong, simmer until vegetables are cooked.", "1 Hour", "Medium"),
        r("Pancit Canton", "egg noodles, pork, shrimp, cabbage, carrot, soy sauce, oyster sauce",
            "1. Stir-fry pork, shrimp, and vegetables.\n2. Add soy sauce, oyster sauce, and broth.\n3. Toss in egg noodles until cooked and sauce is absorbed.", "30 Mins", "Medium"),
        r("Beef Caldereta", "beef, tomato sauce, potato, carrot, bell pepper, liver spread",
            "1. Saute garlic and onion, brown the beef.\n2. Add tomato sauce and water, simmer until beef is tender.\n3. Stir in potatoes, carrots, bell peppers, and liver spread.", "2 Hours", "Hard"),
        r("Kare-Kare", "oxtail, peanut butter, eggplant, string beans, bok choy, shrimp paste (bagoong)",
            "1. Boil oxtail until very tender.\n2. Thicken broth with peanut butter and toasted rice flour.\n3. Add vegetables. Serve with sauteed shrimp paste on the side.", "2.5 Hours", "Hard"),
        r("Tinolang Manok", "chicken, papaya, ginger, onion, spinach",
            "1. Saute ginger, onion, and chicken.\n2. Add water and simmer until chicken is cooked.\n3. Add green papaya and spinach leaves.", "45 Mins", "Medium"),
        r("Pork Sisig", "pork belly, onion, chili, calamansi, egg, mayonnaise",
            "1. Boil, grill, and finely chop pork belly.\n2. Sizzle in a hot pan with onions and chilies.\n3. Mix with calamansi, mayo, and top with a raw egg.", "1.5 Hours", "Hard"),
        r("Lechon Kawali", "pork belly, garlic, bay leaf, peppercorn, oil",
            "1. Boil pork belly with garlic, bay leaf, and peppercorns until tender.\n2. Air dry the pork.\n3. Deep fry until the skin is crispy and blistered.", "1.5 Hours", "Hard"),
        r("Bistek Tagalog", "beef, soy sauce, calamansi, onion",
            "1. Marinate beef slices in soy sauce and calamansi.\n2. Pan-fry beef, then saute rings of onion.\n3. Pour marinade into the pan, simmer briefly and serve.", "40 Mins", "Medium"),
        r("Lumpia Shanghai", "ground pork, carrot, onion, garlic, lumpia wrapper",
            "1. Mix ground pork with minced carrot, onion, and garlic.\n2. Roll mixture tightly in lumpia wrappers.\n3. Deep fry until golden brown. Serve with sweet chili sauce.", "45 Mins", "Medium"),
        r("Chicken Inasal", "chicken, lemongrass, ginger, garlic, calamansi, annatto oil",
            "1. Marinate chicken in lemongrass, ginger, garlic, and calamansi.\n2. Grill over hot coals.\n3. Baste frequently with annatto oil for color and flavor.", "1 Hour", "Medium"),
        r("Monggo Guisado", "mung beans (monggo), pork, spinach, tomato, garlic, onion",
            "1. Boil mung beans until mushy.\n2. Saute garlic, onion, tomato, and small pork pieces.\n3. Combine with beans, simmer, and add spinach leaves before serving.", "50 Mins", "Medium"),
        r("Tortang Talong", "eggplant, egg, salt",
            "1. Grill eggplant until skin is charred, then peel.\n2. Flatten the eggplant and dip in beaten egg.\n3. Pan-fry until golden brown.", "20 Mins", "Easy"),
        r("Ginisang Ampalaya", "bitter gourd (ampalaya), egg, tomato, onion, garlic",
            "1. Thinly slice ampalaya and soak in salt water to reduce bitterness.\n2. Saute garlic, onion, and tomato.\n3. Add ampalaya, then scramble an egg into the mixture.", "20 Mins", "Medium"),
        r("Bicol Express", "pork belly, coconut milk, shrimp paste (bagoong), green chili, garlic, onion",
            "1. Saute garlic, onion, and pork.\n2. Add shrimp paste and coconut milk.\n3. Add plenty of sliced green chilies and simmer until sauce thickens.", "50 Mins", "Medium"),
        r("Tocino", "pork, sugar, garlic, soy sauce",
            "1. Cure sliced pork in sugar, garlic, and a splash of soy sauce.\n2. Fry in a pan with a little water until the water evaporates and the pork caramelizes."),
        r("Longganisa", "ground pork, garlic, sugar, vinegar, black pepper",
            "1. Mix ground pork with heavy garlic, sugar, vinegar, and pepper.\n2. Stuff into casings or shape into skinless patties.\n3. Pan-fry until caramelized.")
    )

    fun updateLlmCache(newRecipes: List<Recipe>) {
        for (r in newRecipes) {
            if (llmRecipesCache.none { it.name.equals(r.name, ignoreCase = true) }) {
                llmRecipesCache.add(r)
            }
        }
    }

    val allRecipes: List<Recipe> get() {
        val list = recipes.toMutableList()
        for (r in llmRecipesCache) {
            if (list.none { it.name.equals(r.name, ignoreCase = true) }) {
                list.add(r)
            }
        }
        return list
    }

    fun normalize(s: String): String {
        val t = s.trim().lowercase()
        return when (t) {
            "adobe" -> "adobo"
            else -> when {
                t.endsWith("ies") -> t.dropLast(3) + "y"
                t.endsWith("oes") -> t.dropLast(2)
                t.endsWith("s") && !t.endsWith("ss") -> t.dropLast(1)
                else -> t
            }
        }
    }

    val knownIngredients: Set<String> get() {
        val combined = recipes.flatMap { it.ingredients }.map { normalize(it) }.toMutableSet()
        combined.addAll(llmRecipesCache.flatMap { it.ingredients }.map { normalize(it) })
        return combined
    }

    fun suggest(userInputs: List<String>): List<Match> {
        val normalizedInputs = userInputs.map { normalize(it) }
        return allRecipes.mapNotNull { rec ->
            val recNameLower = rec.name.lowercase()
            val matchesName = userInputs.any { input ->
                val normInput = normalize(input)
                recNameLower.contains(normInput) || recNameLower.contains(input.lowercase())
            }

            val have = rec.ingredients.filter { ing ->
                val normIng = normalize(ing)
                normalizedInputs.any { normInput -> normIng == normInput || normIng.contains(normInput) }
            }

            if (!matchesName && have.isEmpty() && userInputs.isNotEmpty()) {
                null
            } else {
                Match(rec, have, rec.ingredients.filter { it !in have })
            }
        }.sortedWith(compareByDescending<Match> { it.ratio }.thenBy { it.missing.size })
    }

    /** Fetches precise recipe suggestions using `freellmapi` or Pollinations OpenAI-compatible endpoint. */
    fun fetchLlmRecipe(query: String, callback: (List<Recipe>) -> Unit) {
        if (query.isBlank()) {
            callback(emptyList())
            return
        }
        
        val isPollinations = llmBaseUrl.contains("pollinations")
        val url = if (isPollinations) llmBaseUrl else "$llmBaseUrl/chat/completions"

        val jsonBody = JsonObject().apply {
            if (!isPollinations) addProperty("model", "auto")
            addProperty("jsonMode", true)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", "You are HappiMeal AI, the official intelligent AI chef assistant for the HappiMeal app. You are an absolute expert in ALL Filipino cuisine, including regional dishes from Luzon, Visayas, and Mindanao (such as Dinuguan, Pinakbet, Kare-Kare, Lechon, Sisig, Laing, Bulalo, Kinilaw, and every other local delicacy). If the user searches for ANY Filipino food, instantly generate a highly authentic and precise recipe for it. You also expertly support all foods from other countries globally. Return ONLY valid JSON with no markdown formatting matching this exact structure: {\"name\": \"Dish Name\", \"ingredients\": [\"ing1\", \"ing2\"], \"steps\": \"1. Step one\\n2. Step two\", \"time\": \"Estimated cooking time (e.g. 45 Mins)\", \"difficulty\": \"Easy/Medium/Hard\"}")
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", "Generate recipe for: $query")
                })
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $llmApiKey")
            .post(requestBody)
            .build()

        Thread {
            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val respString = response.body?.string() ?: ""
                    
                    val content = if (isPollinations) {
                        respString.trim()
                    } else {
                        val root = JsonParser.parseString(respString).asJsonObject
                        val choices = root.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            choices.get(0).asJsonObject.getAsJsonObject("message").get("content").asString.trim()
                        } else ""
                    }
                    
                    if (content.isNotBlank()) {
                        val cleanJson = content
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()

                        val recipeObj = JsonParser.parseString(cleanJson).asJsonObject
                        val name = recipeObj.get("name").asString
                        val steps = recipeObj.get("steps").asString
                        val time = if (recipeObj.has("time")) recipeObj.get("time").asString else "30 Mins"
                        val difficulty = if (recipeObj.has("difficulty")) recipeObj.get("difficulty").asString else "Medium"
                        val ingArray = recipeObj.getAsJsonArray("ingredients")
                        val ingredientsList = ingArray.map { it.asString.trim() }

                        val recipe = Recipe(name = name, ingredients = ingredientsList, steps = steps, time = time, difficulty = difficulty)
                        if (llmRecipesCache.none { it.name.equals(name, ignoreCase = true) }) {
                            llmRecipesCache.add(recipe)
                        }
                        callback(listOf(recipe))
                        return@Thread
                    }
                }
            } catch (e: Exception) {
                // LLM offline or unreachable
            }
            // Fallback to TheMealDB if LLM fails
            fetchPublicDbRecipe(query, callback)
        }.start()
    }



    /** Fallback: Fetches from TheMealDB public API if the LLM server is not available */
    private fun fetchPublicDbRecipe(query: String, callback: (List<Recipe>) -> Unit) {
        val encoded = try {
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        } catch (e: Exception) {
            query
        }

        val searchUrl = "https://www.themealdb.com/api/json/v1/1/search.php?s=$encoded"
        val filterUrl = "https://www.themealdb.com/api/json/v1/1/filter.php?i=$encoded"

        val onlineRecipes = mutableListOf<Recipe>()

        try {
            val req = Request.Builder().url(searchUrl).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                val detailResponse = gson.fromJson(body, MealDetailResponse::class.java)
                detailResponse?.meals?.take(5)?.forEach {
                    onlineRecipes.add(it.toRecipe())
                }
            }
        } catch (e: Exception) {
            // ignore
        }

        if (onlineRecipes.isEmpty()) {
            try {
                val req = Request.Builder().url(filterUrl).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    val searchResponse = gson.fromJson(body, MealSearchResponse::class.java)
                    searchResponse?.meals?.take(5)?.forEach { summary ->
                        val detailUrl = "https://www.themealdb.com/api/json/v1/1/lookup.php?i=${summary.idMeal}"
                        val detailReq = Request.Builder().url(detailUrl).build()
                        try {
                            val detailResp = client.newCall(detailReq).execute()
                            if (detailResp.isSuccessful) {
                                val detailBody = detailResp.body?.string()
                                val detailResponse = gson.fromJson(detailBody, MealDetailResponse::class.java)
                                detailResponse?.meals?.firstOrNull()?.let {
                                    onlineRecipes.add(it.toRecipe())
                                }
                            }
                        } catch (e: Exception) {
                            // skip
                        }
                    }
                }
            } catch (e: Exception) {
                // ignore
            }
        }
        
        if (onlineRecipes.isNotEmpty()) {
            updateLlmCache(onlineRecipes)
        }
        callback(onlineRecipes)
    }
}
