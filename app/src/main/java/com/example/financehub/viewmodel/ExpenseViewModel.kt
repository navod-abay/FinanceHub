import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.financehub.data.database.Expense
import kotlinx.coroutines.launch

class ExpenseViewModel(private val repository: ExpenseRepository) : ViewModel() {
    fun addExpense(title: String, amount: Double) {
        viewModelScope.launch {
            repository.insertExpense(Expense(title = title, amount = amount))
        }
    }
}
