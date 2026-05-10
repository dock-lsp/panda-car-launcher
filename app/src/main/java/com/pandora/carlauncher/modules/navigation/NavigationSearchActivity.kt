package com.pandora.carlauncher.modules.navigation

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pandora.carlauncher.R
import com.pandora.carlauncher.databinding.ActivityNavigationSearchBinding

/**
 * 导航搜索Activity
 * 
 * 提供导航目的地搜索功能
 */
class NavigationSearchActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NavigationSearchActivity"
        
        // 搜索历史最大数量
        private const val MAX_HISTORY = 10
    }
    
    private lateinit var binding: ActivityNavigationSearchBinding
    
    // 搜索历史
    private val searchHistory = mutableListOf<String>()
    
    // 搜索建议
    private val suggestions = mutableListOf<SearchSuggestion>()
    
    // 适配器
    private lateinit var historyAdapter: SearchHistoryAdapter
    private lateinit var suggestionAdapter: SearchSuggestionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityNavigationSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupListeners()
        loadSearchHistory()
        
        // 自动弹出键盘
        showKeyboard()
    }

    /**
     * 设置UI
     */
    private fun setupUI() {
        // 历史记录列表
        historyAdapter = SearchHistoryAdapter(
            history = searchHistory,
            onItemClick = { keyword -> search(keyword) },
            onDeleteClick = { position ->
                searchHistory.removeAt(position)
                historyAdapter.notifyDataSetChanged()
            }
        )
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = historyAdapter
        
        // 搜索建议列表
        suggestionAdapter = SearchSuggestionAdapter(suggestions) { suggestion ->
            selectSuggestion(suggestion)
        }
        binding.recyclerSuggestion.layoutManager = LinearLayoutManager(this)
        binding.recyclerSuggestion.adapter = suggestionAdapter
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }
        
        // 清除按钮
        binding.btnClear.setOnClickListener {
            binding.etSearch.setText("")
        }
        
        // 搜索输入
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val keyword = s?.toString() ?: ""
                binding.btnClear.visibility = if (keyword.isEmpty()) View.GONE else View.VISIBLE
                
                if (keyword.isNotEmpty()) {
                    searchSuggestions(keyword)
                } else {
                    showHistory()
                }
            }
        })
        
        // 键盘搜索按钮
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            val keyword = binding.etSearch.text.toString()
            if (keyword.isNotEmpty()) {
                search(keyword)
            }
            true
        }
        
        // 清空历史按钮
        binding.btnClearHistory.setOnClickListener {
            clearHistory()
        }
        
        // POI分类
        binding.chipHome.setOnClickListener { search("家") }
        binding.chipCompany.setOnClickListener { search("公司") }
        binding.chipGasStation.setOnClickListener { search("加油站") }
        binding.chipParking.setOnClickListener { search("停车场") }
        binding.chipRestaurant.setOnClickListener { search("餐厅") }
        binding.chipHotel.setOnClickListener { search("酒店") }
        binding.chipBank.setOnClickListener { search("银行") }
        binding.chipHospital.setOnClickListener { search("医院") }
    }

    /**
     * 加载搜索历史
     */
    private fun loadSearchHistory() {
        // 从SharedPreferences加载历史
        val prefs = getSharedPreferences("nav_search", MODE_PRIVATE)
        val historyStr = prefs.getString("history", "") ?: ""
        
        searchHistory.clear()
        if (historyStr.isNotEmpty()) {
            searchHistory.addAll(historyStr.split("|").filter { it.isNotBlank() })
        }
        
        showHistory()
    }

    /**
     * 保存搜索历史
     */
    private fun saveSearchHistory() {
        val prefs = getSharedPreferences("nav_search", MODE_PRIVATE)
        val historyStr = searchHistory.joinToString("|")
        prefs.edit().putString("history", historyStr).apply()
    }

    /**
     * 添加到搜索历史
     */
    private fun addToHistory(keyword: String) {
        // 移除已存在的
        searchHistory.remove(keyword)
        
        // 添加到开头
        searchHistory.add(0, keyword)
        
        // 限制数量
        while (searchHistory.size > MAX_HISTORY) {
            searchHistory.removeAt(searchHistory.size - 1)
        }
        
        saveSearchHistory()
    }

    /**
     * 清空搜索历史
     */
    private fun clearHistory() {
        searchHistory.clear()
        saveSearchHistory()
        showHistory()
    }

    /**
     * 显示历史记录
     */
    private fun showHistory() {
        binding.layoutHistory.visibility = if (searchHistory.isEmpty()) View.GONE else View.VISIBLE
        binding.recyclerHistory.visibility = if (searchHistory.isEmpty()) View.GONE else View.VISIBLE
        binding.tvHistoryTitle.visibility = if (searchHistory.isEmpty()) View.GONE else View.VISIBLE
        binding.btnClearHistory.visibility = if (searchHistory.isEmpty()) View.GONE else View.VISIBLE
        
        binding.layoutSuggestion.visibility = View.GONE
        binding.recyclerSuggestion.visibility = View.GONE
        
        historyAdapter.notifyDataSetChanged()
    }

    /**
     * 搜索建议
     */
    private fun searchSuggestions(keyword: String) {
        // 模拟搜索建议（实际应该调用地图SDK）
        suggestions.clear()
        
        val mockSuggestions = listOf(
            SearchSuggestion(keyword + "大厦", "商务大厦", "1.2km"),
            SearchSuggestion(keyword + "广场", "购物中心", "2.5km"),
            SearchSuggestion(keyword + "路", "道路", "0.8km"),
            SearchSuggestion(keyword + "酒店", "住宿", "3.1km")
        )
        
        suggestions.addAll(mockSuggestions)
        
        binding.layoutSuggestion.visibility = View.VISIBLE
        binding.recyclerSuggestion.visibility = View.VISIBLE
        binding.layoutHistory.visibility = View.GONE
        binding.recyclerHistory.visibility = View.GONE
        binding.tvHistoryTitle.visibility = View.GONE
        binding.btnClearHistory.visibility = View.GONE
        
        suggestionAdapter.notifyDataSetChanged()
    }

    /**
     * 执行搜索
     */
    private fun search(keyword: String) {
        binding.etSearch.setText(keyword)
        addToHistory(keyword)
        
        // TODO: 调用地图SDK进行路径规划
        Toast.makeText(this, "正在搜索: $keyword", Toast.LENGTH_SHORT).show()
        
        // 返回导航页面
        finish()
    }

    /**
     * 选择搜索建议
     */
    private fun selectSuggestion(suggestion: SearchSuggestion) {
        search(suggestion.name)
    }

    /**
     * 显示键盘
     */
    private fun showKeyboard() {
        binding.etSearch.requestFocus()
        
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }
}

/**
 * 搜索建议数据类
 */
data class SearchSuggestion(
    val name: String,
    val category: String,
    val distance: String
)

/**
 * 搜索历史适配器
 */
class SearchHistoryAdapter(
    private val history: List<String>,
    private val onItemClick: (String) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvKeyword: TextView = view.findViewById(R.id.tv_keyword)
        val btnDelete: ImageButton = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val keyword = history[position]
        holder.tvKeyword.text = keyword
        holder.tvKeyword.setOnClickListener { onItemClick(keyword) }
        holder.btnDelete.setOnClickListener {
            onDeleteClick(position)
        }
    }

    override fun getItemCount(): Int = history.size
}

/**
 * 搜索建议适配器
 */
class SearchSuggestionAdapter(
    private val suggestions: List<SearchSuggestion>,
    private val onItemClick: (SearchSuggestion) -> Unit
) : RecyclerView.Adapter<SearchSuggestionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_name)
        val tvCategory: TextView = view.findViewById(R.id.tv_category)
        val tvDistance: TextView = view.findViewById(R.id.tv_distance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val suggestion = suggestions[position]
        holder.tvName.text = suggestion.name
        holder.tvCategory.text = suggestion.category
        holder.tvDistance.text = suggestion.distance
        
        holder.itemView.setOnClickListener { onItemClick(suggestion) }
    }

    override fun getItemCount(): Int = suggestions.size
}
