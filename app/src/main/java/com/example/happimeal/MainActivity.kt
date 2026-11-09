package com.example.happimeal

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.File

/** Card-style row for each suggested meal. */
class MealAdapter(context: Context, private val items: MutableList<Match>) :
    ArrayAdapter<Match>(context, 0, items) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_meal, parent, false)
        val m = items[position]
        val pct = (m.ratio * 100).toInt()

        view.findViewById<TextView>(R.id.tvName).text = m.recipe.name
        view.findViewById<TextView>(R.id.tvPercent).apply {
            text = "$pct% match"
            setTextColor(
                when {
                    pct == 100 -> Color.parseColor("#2E7D32")
                    pct >= 50 -> Color.parseColor("#EF6C00")
                    else -> Color.parseColor("#757575")
                }
            )
        }
        view.findViewById<TextView>(R.id.tvMissing).text =
            if (m.missing.isEmpty()) "✅ You have everything!"
            else "Missing: ${m.missing.joinToString(", ")}"
        return view
    }
}

class MainActivity : AppCompatActivity() {

    private val ingredients = mutableListOf<String>()
    private val matches = mutableListOf<Match>()

    private lateinit var mealAdapter: MealAdapter
    private lateinit var input: EditText
    private lateinit var emptyText: TextView
    private lateinit var noIngredientsText: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var pbLoading: ProgressBar

    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS) }

    // Opens our in-app camera screen (CameraActivity) and receives the saved photo path.
    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val path = result.data?.getStringExtra(CameraActivity.EXTRA_PHOTO_PATH)
                if (path != null) analyzeUri(Uri.fromFile(File(path)))
            }
        }

    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) analyzeUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawerLayout)
        // Set up TopAppBar to open drawer
        val topAppBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        topAppBar.setNavigationOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val headerView = navigationView.getHeaderView(0)
        val btnEditProfile = headerView.findViewById<View>(R.id.btnEditProfile)

        btnEditProfile.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, ProfileActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
                R.id.nav_logout -> {
                    // Log out
                    startActivity(Intent(this, LoginActivity::class.java))
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    finish()
                }
            }
            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            spinIcon(menuItem)
            when (menuItem.itemId) {
                R.id.bottom_nav_home -> true
                R.id.bottom_nav_history -> {
                    // Delay slightly to let the 0.5s spin animation play before cross-fading
                    bottomNavigationView.postDelayed({
                        startActivity(Intent(this, HistoryActivity::class.java))
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }, 300)
                    true
                }
                else -> false
            }
        }
        bottomNavigationView.setOnItemReselectedListener { spinIcon(it) }

        input = findViewById(R.id.etIngredient)
        emptyText = findViewById(R.id.tvEmpty)
        noIngredientsText = findViewById(R.id.tvNoIngredients)
        chipGroup = findViewById(R.id.chipGroup)
        pbLoading = findViewById(R.id.pbLoading)
        val resultList = findViewById<ListView>(R.id.lvResults)

        mealAdapter = MealAdapter(this, matches)
        resultList.adapter = mealAdapter

        findViewById<Button>(R.id.btnAdd).setOnClickListener { addFromInput() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addFromInput()
                true
            } else false
        }

        findViewById<Button>(R.id.btnCamera).setOnClickListener {
            cameraLauncher.launch(Intent(this, CameraActivity::class.java))
        }
        findViewById<Button>(R.id.btnGallery).setOnClickListener { pickPhoto.launch("image/*") }
        findViewById<Button>(R.id.btnClear).setOnClickListener {
            ingredients.clear()
            refresh()
        }
        
        findViewById<Button>(R.id.btnFindRecipes).setOnClickListener {
            if (ingredients.isEmpty()) {
                toast("Please add some ingredients first!")
            } else {
                fetchOnlineRecipes()
            }
        }

        resultList.setOnItemClickListener { _, _, pos, _ ->
            val m = matches[pos]
            HistoryManager.saveToHistory(this@MainActivity, m) // Save to history
            val intent = Intent(this, RecipeDetailActivity::class.java).apply {
                putExtra("NAME", m.recipe.name)
                putExtra("TIME", m.recipe.time)
                putExtra("DIFFICULTY", m.recipe.difficulty)
                putStringArrayListExtra("HAVE", ArrayList(m.have))
                putStringArrayListExtra("MISSING", ArrayList(m.missing))
                putExtra("STEPS", m.recipe.steps)
            }
            startActivity(intent)
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        refresh()
    }

    private fun addFromInput() {
        val text = input.text.toString()
        if (text.isNotBlank()) {
            text.split(",").forEach { addIngredient(it) }
            input.text.clear()
            refresh()
        }
    }

    /** Adds one ingredient if it's not blank or a duplicate. Returns true if added. */
    private fun addIngredient(raw: String): Boolean {
        val name = raw.trim().lowercase()
        if (name.isEmpty()) return false
        val key = RecipeRepository.normalize(name)
        if (ingredients.any { RecipeRepository.normalize(it) == key }) return false
        ingredients.add(name)
        return true
    }

    private fun analyzeUri(uri: Uri) {
        try {
            analyze(InputImage.fromFilePath(this, uri))
        } catch (e: Exception) {
            toast("Could not read image")
        }
    }

    private fun analyze(image: InputImage) {
        toast("Analyzing photo...")
        labeler.process(image)
            .addOnSuccessListener { labels ->
                val detected = labels.filter { it.confidence >= 0.5f }.map { it.text }
                val added = detected.filter {
                    RecipeRepository.normalize(it) in RecipeRepository.knownIngredients && addIngredient(it)
                }
                refresh()
                val msg = if (added.isNotEmpty()) "Added: ${added.joinToString(", ")}"
                else "No known ingredients found. Detected: ${detected.joinToString(", ").ifEmpty { "nothing" }}. Add manually instead."
                AlertDialog.Builder(this)
                    .setTitle("Photo result")
                    .setMessage(msg + "\n\nPhoto detection is approximate - tap a chip to remove wrong ones, or type missing ones.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            .addOnFailureListener { toast("Photo analysis failed: ${it.message}") }
    }

    override fun onResume() {
        super.onResume()
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        bottomNavigationView.selectedItemId = R.id.bottom_nav_home
        updateNavHeader()
    }

    private fun updateNavHeader() {
        val navigationView = findViewById<NavigationView>(R.id.navigationView)
        val headerView = navigationView.getHeaderView(0)
        val tvHeaderName = headerView.findViewById<TextView>(R.id.tvHeaderName)
        val tvHeaderEmail = headerView.findViewById<TextView>(R.id.tvHeaderEmail)
        val ivAvatar = headerView.findViewById<ImageView>(R.id.ivAvatar)

        val prefs = getSharedPreferences("HappiMealPrefs", MODE_PRIVATE)
        tvHeaderName.text = prefs.getString("USERNAME", "Guest User")
        tvHeaderEmail.text = prefs.getString("EMAIL", "guest@example.com")

        val avatarFile = File(filesDir, "avatar.jpg")
        if (avatarFile.exists()) {
            ivAvatar.setImageURI(null) // force refresh layout
            ivAvatar.setImageURI(Uri.fromFile(avatarFile))
        } else {
            ivAvatar.setImageResource(R.drawable.ic_account_circle)
        }
    }

    private fun refresh() {
        // Rebuild ingredient chips
        chipGroup.removeAllViews()
        ingredients.toList().forEach { name ->
            val chip = Chip(this).apply {
                text = name
                isCloseIconVisible = true
                setOnClickListener { ingredients.remove(name); refresh() }
                setOnCloseIconClickListener { ingredients.remove(name); refresh() }
            }
            chipGroup.addView(chip)
        }
        noIngredientsText.visibility = if (ingredients.isEmpty()) View.VISIBLE else View.GONE

        // Rebuild meal suggestions using local RecipeRepository
        matches.clear()
        matches.addAll(RecipeRepository.suggest(ingredients))
        mealAdapter.notifyDataSetChanged()
        emptyText.text = when {
            ingredients.isEmpty() -> "Add ingredients to get meal suggestions."
            matches.isEmpty() -> "No meals found for these ingredients."
            else -> ""
        }
    }

    private fun fetchOnlineRecipes() {
        if (ingredients.isEmpty()) return
        val queryStr = ingredients.joinToString(", ")
        pbLoading.visibility = View.VISIBLE
        RecipeRepository.fetchLlmRecipe(queryStr) { llmRecipes ->
            runOnUiThread {
                pbLoading.visibility = View.GONE
                if (llmRecipes.isNotEmpty()) {
                    RecipeRepository.updateLlmCache(llmRecipes)
                    matches.clear()
                    matches.addAll(RecipeRepository.suggest(ingredients))
                    mealAdapter.notifyDataSetChanged()
                    emptyText.text = if (matches.isEmpty()) "No meals found for these ingredients." else ""
                    toast("✨ Found new recipes!")
                } else {
                    toast("No new recipes found online.")
                }
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun spinIcon(item: MenuItem) {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        val itemView = nav.findViewById<ViewGroup>(item.itemId)
        for (i in 0 until (itemView?.childCount ?: 0)) {
            val child = itemView?.getChildAt(i)
            if (child is ImageView) {
                // Rotate 360 degrees over 0.5 seconds
                child.animate().rotationBy(360f).setDuration(500).start()
                break
            }
        }
    }
}