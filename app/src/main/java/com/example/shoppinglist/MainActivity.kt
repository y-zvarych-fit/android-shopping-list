package com.example.shoppinglist

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.shoppinglist.ui.theme.ShoppinglistTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShoppinglistTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        ShoppingListScreen()
                    }
                }
            }
        }
    }
}

// Model View ViewModel

@Entity(tableName = "shopping_items")
data class ShoppingItem (
    val name: String,
    var isBought: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Int = 0
)

@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY id DESC")
    fun getAllItems(): List<ShoppingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: ShoppingItem)

    @Update
    fun updateItem(item: ShoppingItem)

    @Delete
    fun deleteItem(item: ShoppingItem)
}

@Database(entities = [ShoppingItem::class], version = 1)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        fun getInstance(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ShoppingListViewModel(application: Application) : AndroidViewModel(application) {

    private val dao: ShoppingDao = ShoppingDatabase.getInstance(application).shoppingDao()
    private val _shoppingList = mutableStateListOf<ShoppingItem>()
    val shoppingList: List<ShoppingItem> get() = _shoppingList

    init {
        loadShoppingList()
    }

    private fun loadShoppingList() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = dao.getAllItems()
            _shoppingList.clear()
            _shoppingList.addAll(items)
        }
    }

    fun addItem(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val newItem = ShoppingItem(name = name)
            dao.insertItem(newItem)
            loadShoppingList()
        }
    }

    fun toggleBought(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _shoppingList[index]
            val updatedItem = item.copy(isBought = !item.isBought)
            dao.updateItem(updatedItem)
            _shoppingList[index] = updatedItem
        }
    }

    fun deleteItem(item: ShoppingItem) { // --- New Functionality ---
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteItem(item)
            loadShoppingList()
        }
    }

    fun updateItem(item: ShoppingItem) { // --- New Functionality ---
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateItem(item)
            loadShoppingList()
        }
    }
}

@Composable
fun ShoppingItemCard(item: ShoppingItem,
                     onToggleBought: () -> Unit = {},
                     onDelete: () -> Unit = {},        // --- New Functionality ---
                     onEdit: () -> Unit = {}) {
    val bgColor by animateColorAsState(  // --- New Functionality ---
        targetValue = if (item.isBought) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        else MaterialTheme.colorScheme.surfaceDim,
        label = "checkbox animation"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .background(
                bgColor,
                MaterialTheme.shapes.large
            )
            .clickable { onToggleBought() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.isBought, onCheckedChange = {
            onToggleBought()
        })
        Text(text = item.name, fontSize = 18.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onEdit) {      // --- New Functionality ---
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {    // --- New Functionality ---
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

class ShoppingListViewModelFactory(private val application: Application) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShoppingListViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun AddItemButton(addItem: (String) -> Unit = {}) {
    var text by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Add Item") }
        )
        Button(onClick = {
            if (text.isNotEmpty()) {
                addItem(text)
                text = ""
            }
        }) {
            Text("Add")
        }
    }
}

@Composable
fun EditItemDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Item") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it })
        },
        confirmButton = {
            TextButton(onClick = {
                if (text.isNotEmpty()) onConfirm(text)
            }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModelFactory(
    LocalContext.current.applicationContext as Application))) {
    var editingItem by remember { mutableStateOf<ShoppingItem?>(null) } // --- New Functionality ---

    val boughtCount = viewModel.shoppingList.count { it.isBought }      // --- New Functionality ---

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            AddItemButton { viewModel.addItem(it) }
            Text( // --- New Functionality ---
                text = "Bought items: $boughtCount",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        itemsIndexed(viewModel.shoppingList) { ix, item ->
            ShoppingItemCard(
                item = item,
                onToggleBought = { viewModel.toggleBought(ix) },
                onDelete = { viewModel.deleteItem(item) },                          // --- New Functionality ---
                onEdit = { editingItem = item }                                    // --- New Functionality ---
            )
        }
    }

    // --- New Functionality ---
    editingItem?.let { item ->
        EditItemDialog(
            initialText = item.name,
            onConfirm = {
                val updated = item.copy(name = it)
                viewModel.updateItem(updated)
                editingItem = null
            },
            onDismiss = { editingItem = null }
        )
    }
}

@Preview
@Composable
fun ShoppingListScreenPreview() {
    ShoppingListScreen()
}

//@Preview(showBackground = true)
@Composable
fun ShoppingItemCardPreview() {
    var toggleState by remember { mutableStateOf(false) }
    ShoppingItemCard(ShoppingItem("Milk", isBought = toggleState)) {
        toggleState = !toggleState
    }
}
