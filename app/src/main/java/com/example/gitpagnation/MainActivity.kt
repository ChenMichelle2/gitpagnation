package com.example.gitpagnation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gitpagnation.ui.theme.GitpagnationTheme
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.items
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory


// Data class
data class Repo(
    val id: Long,
    val name: String,
    val description: String?
)

// Retrofit API for GitHub
interface GitHubService {
    @GET("users/{user}/repos")
    suspend fun getRepos(
        @Path("user") user: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 30
    ): Response<List<Repo>>
}
object RetrofitClient {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val gitHubService: GitHubService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GitHubService::class.java)
    }
}

//represent UI state
data class RepoState(
    val repos: List<Repo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false
)

class RepoViewModel : ViewModel() {

    private val _state = MutableStateFlow(RepoState())
    val state: StateFlow<RepoState> = _state

    //current page and user
    private var currentPage = 1
    var currentUser: String? = null

    fun loadReposForUser(username: String, reset: Boolean = true) {
        if (reset) {
            currentPage = 1
            currentUser = username
            _state.value = RepoState(isLoading = true)
        } else {
            _state.value = _state.value.copy(isLoading = true)
        }

        viewModelScope.launch {
            try {
                val response = RetrofitClient.gitHubService.getRepos(
                    user = username,
                    page = currentPage
                )
                if (response.isSuccessful) {
                    val newRepos = response.body() ?: emptyList()
                    val linkHeader = response.headers()["Link"]
                    val hasMorePages = linkHeader?.contains("rel=\"next\"") ?: false

                    //Update list
                    val updatedList = if (reset) {
                        newRepos
                    } else {
                        _state.value.repos + newRepos
                    }

                    _state.value = _state.value.copy(
                        repos = updatedList,
                        isLoading = false,
                        error = null,
                        hasMore = hasMorePages
                    )
                    if (hasMorePages) currentPage++
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Error: ${response.code()} ${response.message()}"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.localizedMessage
                )
            }
        }
    }
}



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitpagnationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RepoScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}


@Composable
fun RepoScreen(modifier: Modifier = Modifier, viewModel: RepoViewModel = viewModel()) {
    var username by remember { mutableStateOf("") }
    val state by viewModel.state.collectAsState()

    Column(modifier = modifier.padding(16.dp)) {
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("GitHub Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = { viewModel.loadReposForUser(username, reset = true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Search")
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Loading
        if (state.isLoading && state.repos.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }

        // error message
        state.error?.let { errorMsg ->
            Text(text = errorMsg, color = MaterialTheme.colorScheme.error)
        }

        // Display repos
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.repos) { repo ->
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(text = repo.name, style = MaterialTheme.typography.titleMedium)
                    repo.description?.let { description ->
                        Text(text = description, style = MaterialTheme.typography.bodyMedium)
                    }
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                }
            }
        }


        if (state.isLoading && state.repos.isNotEmpty()) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun RepoScreenPreview() {
    GitpagnationTheme {
        RepoScreen()
    }
}