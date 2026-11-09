package com.example.happimeal

data class Recipe(
    val name: String, 
    val ingredients: List<String>, 
    val steps: String,
    val time: String = "30 Mins",
    val difficulty: String = "Medium"
)

data class Match(val recipe: Recipe, val have: List<String>, val missing: List<String>) {
    val ratio: Float get() = have.size.toFloat() / (have.size + missing.size)
}