package com.iptv.industv

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {
    private val ALL = "All"
    private val FAV = "★ Favorites"
    private val ALL_LANG = "All Languages"
    private val OTHER = "Other"

    private lateinit var grid: RecyclerView
    private lateinit var cats: RecyclerView
    private lateinit var search: EditText
    private lateinit var progress: ProgressBar
    private lateinit var empty: TextView
    private lateinit var title: TextView
    private lateinit var adapter: ChannelAdapter
    private lateinit var catAdapter: CategoryAdapter
    private lateinit var langs: RecyclerView
    private lateinit var langAdapter: CategoryAdapter
    private var category = ALL
    private var language = ALL_LANG
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Repo.init(this)

        grid = findViewById(R.id.grid)
        cats = findViewById(R.id.cats)
        search = findViewById(R.id.search)
        progress = findViewById(R.id.progress)
        empty = findViewById(R.id.empty)
        title = findViewById(R.id.title)

        val widthDp = resources.displayMetrics.run { widthPixels / density }
        grid.layoutManager = GridLayoutManager(this, maxOf(2, (widthDp / 170).toInt()))
        grid.setHasFixedSize(false)
        adapter = ChannelAdapter(
            onClick = { pos ->
                Repo.queue = Repo.shown
                Repo.index = pos
                startActivity(Intent(this, PlayerActivity::class.java))
            },
            onLong = { c ->
                val on = Repo.toggleFav(this, c.url)
                Toast.makeText(this, if (on) "Added to favorites" else "Removed from favorites", Toast.LENGTH_SHORT).show()
                if (category == FAV) applyFilter()
            },
            isFav = { Repo.isFav(it.url) }
        )
        grid.adapter = adapter

        cats.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        catAdapter = CategoryAdapter { selectCategory(it) }
        cats.adapter = catAdapter

        langs = findViewById(R.id.langs)
        langs.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        langAdapter = CategoryAdapter { selectLanguage(it) }
        langAdapter.selected = ALL_LANG
        langs.adapter = langAdapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(e: Editable?) { query = e?.toString().orEmpty(); applyFilter() }
        })
        search.setOnEditorActionListener { v, _, _ ->
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)
            grid.requestFocus()
            true
        }
        findViewById<View>(R.id.btnRefresh).setOnClickListener { loadChannels(true) }
        findViewById<View>(R.id.btnSource).setOnClickListener { showSourceDialog() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    query.isNotEmpty() -> search.setText("")
                    category != ALL -> selectCategory(ALL)
                    language != ALL_LANG -> selectLanguage(ALL_LANG)
                    else -> { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
                }
            }
        })
        loadChannels(false)
    }

    override fun onResume() {
        super.onResume()
        adapter.notifyDataSetChanged() // refresh favorite stars
    }

    private fun loadChannels(force: Boolean) {
        progress.visibility = View.VISIBLE
        empty.visibility = View.GONE
        Repo.load(this, force) { list, msg ->
            progress.visibility = View.GONE
            if (list == null) {
                Repo.all = emptyList()
                adapter.items = emptyList(); adapter.notifyDataSetChanged()
                empty.text = (msg ?: "No channels") + "\n\nCheck internet, then press Refresh or change Source."
                empty.visibility = View.VISIBLE
            } else {
                Repo.all = list
                val groups = list.flatMap { it.groups }.distinct().sortedBy { it.lowercase() }
                catAdapter.items = listOf(ALL, FAV) + groups
                if (category !in catAdapter.items) category = ALL
                catAdapter.selected = category
                catAdapter.notifyDataSetChanged()

                val present = list.flatMap { it.languages }.toSet()
                val langItems = ArrayList<String>()
                langItems.add(ALL_LANG)
                langItems.addAll(Repo.LANGS.filter { it in present })
                if (list.any { it.languages.isEmpty() }) langItems.add(OTHER)
                langAdapter.items = langItems
                if (language !in langItems) language = ALL_LANG
                langAdapter.selected = language
                langAdapter.notifyDataSetChanged()

                applyFilter()
                grid.requestFocus()
                if (msg != null) Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun selectCategory(name: String) {
        category = name
        catAdapter.selected = name
        catAdapter.notifyItemRangeChanged(0, catAdapter.itemCount)
        applyFilter()
    }

    private fun selectLanguage(name: String) {
        language = name
        langAdapter.selected = name
        langAdapter.notifyItemRangeChanged(0, langAdapter.itemCount)
        applyFilter()
    }

    private fun applyFilter() {
        val q = query.trim().lowercase()
        val src = when (category) {
            ALL -> Repo.all
            FAV -> Repo.all.filter { Repo.isFav(it.url) }
            else -> Repo.all.filter { category in it.groups }
        }
        val byLang = when (language) {
            ALL_LANG -> src
            OTHER -> src.filter { it.languages.isEmpty() }
            else -> src.filter { language in it.languages }
        }
        val res = if (q.isEmpty()) byLang else byLang.filter { it.name.lowercase().contains(q) }
        Repo.shown = res
        adapter.items = res
        adapter.notifyDataSetChanged()
        title.text = "Indus TV (${res.size})"
        if (res.isEmpty() && Repo.all.isNotEmpty()) {
            empty.text = if (category == FAV && q.isEmpty()) "No favorites yet.\nLong-press OK on a channel to add one." else "No channels found"
            empty.visibility = View.VISIBLE
        } else if (Repo.all.isNotEmpty()) empty.visibility = View.GONE
    }

    private fun showSourceDialog() {
        val input = EditText(this)
        input.setSingleLine()
        input.setText(Repo.sourceUrl(this))
        AlertDialog.Builder(this)
            .setTitle("Playlist (M3U) URL")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val u = input.text.toString().trim()
                if (u.startsWith("http")) { Repo.setSource(this, u); loadChannels(true) }
                else Toast.makeText(this, "Enter a valid http(s) URL", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Default") { _, _ -> Repo.setSource(this, Repo.DEFAULT_URL); loadChannels(true) }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
