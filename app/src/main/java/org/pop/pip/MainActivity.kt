package org.pop.pip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import kotlinx.coroutines.launch
import org.pop.pip.aur.*
import org.pop.pip.data.DetailModel
import org.pop.pip.data.HttpViewModel
import org.pop.pip.data.SearchPanelModel
import org.pop.pip.db.HistoryDataBase
import org.pop.pip.db.HistoryViewModel
import org.pop.pip.ui.components.*
import org.pop.pip.ui.theme.PopAndPipTheme

class MainActivity : ComponentActivity() {
    private val db by lazy {
        Room.databaseBuilder(applicationContext, HistoryDataBase::class.java, "history.db").build()
    }

    @Suppress("UNCHECKED_CAST")
    private val viewModel by
            viewModels<HistoryViewModel>(
                    factoryProducer = {
                        object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                return HistoryViewModel(db.userHistory()) as T
                            }
                        }
                    }
            )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PopAndPipTheme { Surface(modifier = Modifier.fillMaxSize()) { MainPage(viewModel) } }
        }
    }
}

@Composable
fun MainPage(vm: HistoryViewModel) {

    val navController = rememberNavController()
    val detailModel: DetailModel = viewModel()
    NavHost(navController = navController, startDestination = "main") {
        composable("main") { TopUi(topNav = navController, detailModel = detailModel, vm = vm) }
        composable("DetailPage") {
            DetailPage(navController = navController, detailModel = detailModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailPage(navController: NavController, detailModel: DetailModel) {
    val aurInfo by detailModel.detailData
    if (aurInfo == null) return

    Scaffold(
            topBar = {
                TopAppBar(
                        colors =
                                TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        titleContentColor = MaterialTheme.colorScheme.primary,
                                ),
                        title = { Text(text = aurInfo!!.Name) },
                        navigationIcon = {
                            IconButton(onClick = { navController.navigateUp() }) {
                                Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Localized description"
                                )
                            }
                        },
                )
            }
    ) { padding ->
        AurResultFullCard(
                modifier = Modifier.fillMaxSize().padding(padding).padding(all = 10.dp),
                data = aurInfo!!
        )
    }
}

@Composable
fun TopUi(topNav: NavController, detailModel: DetailModel, vm: HistoryViewModel) {
    val navController = rememberNavController()
    val viewModel: HttpViewModel = viewModel()
    val searchModel: SearchPanelModel = viewModel()
    Scaffold(bottomBar = { PopAndPipBottomBar(listOf("search", "about"), navController) }) { padding
        ->
        NavHost(navController = navController, startDestination = "search") {
            composable("search") {
                SearchResultPage(
                        viewModel = viewModel,
                        searchModel = searchModel,
                        detailModel = detailModel,
                        dp = padding,
                        navController = topNav,
                        vm = vm
                )
            }
            composable("about") { AboutPage(dp = padding) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultPage(
        viewModel: HttpViewModel = viewModel(),
        searchModel: SearchPanelModel = viewModel(),
        detailModel: DetailModel = viewModel(),
        dp: PaddingValues? = null,
        navController: NavController,
        vm: HistoryViewModel
) {
    val dataList = vm.getAllRecords().collectAsState(initial = emptyList())
    var expanded by remember { mutableStateOf(false) }
    val state by viewModel.state
    val searchValue by searchModel.searchValue
    val oldValue by searchModel.oldValue
    var requestType by remember { mutableStateOf(RequestType.Package) }
    var requestTypeOld by remember { mutableStateOf(RequestType.Package) }
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    Column(
            modifier =
                    Modifier.let done@{
                        if (dp == null) return@done it
                        it.padding(dp)
                    }
    ) {
        Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                    onClick = {
                        searchModel.clearData()
                        viewModel.clearStatus()
                    }
            ) { Text("x") }
            PackageSearchBar(
                    modifier = Modifier.weight(1f),
                    searchValue = searchValue,
                    onValueChanged = { value -> searchModel.onValueChanged(value) },
                    onSearch = done@{
                                focusManager.clearFocus()
                                if (oldValue == searchValue &&
                                                requestType.toName() == requestTypeOld.toName() &&
                                                !(state is Resource.Failure)
                                )
                                        return@done
                                requestTypeOld = requestType
                                searchModel.updateOldValue()
                                viewModel.searchPackage(
                                        packageName = searchValue,
                                        requestType = requestType
                                )
                            }
            )
            Spacer(modifier = Modifier.width(2.dp))
            TextButton(
                    onClick = {
                        expanded = !expanded
                        focusManager.clearFocus()
                    }
            ) { Text(text = requestType.toName()) }
            if (expanded) {
                ModalBottomSheet(onDismissRequest = { expanded = false }, sheetState = sheetState) {
                    Column {
                        Text(
                                textAlign = TextAlign.Center,
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clickable {
                                                    requestType = RequestType.Package
                                                    scope
                                                            .launch { sheetState.hide() }
                                                            .invokeOnCompletion { expanded = false }
                                                }
                                                .padding(all = 10.dp),
                                color =
                                        if (requestType == RequestType.Package)
                                                MaterialTheme.colorScheme.primary
                                        else Color.Unspecified,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                text = RequestType.Package.toName()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                                textAlign = TextAlign.Center,
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clickable {
                                                    requestType = RequestType.MakeDepends
                                                    scope
                                                            .launch { sheetState.hide() }
                                                            .invokeOnCompletion { expanded = false }
                                                }
                                                .padding(all = 10.dp),
                                color =
                                        if (requestType == RequestType.MakeDepends)
                                                MaterialTheme.colorScheme.primary
                                        else Color.Unspecified,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                text = RequestType.MakeDepends.toName()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                                textAlign = TextAlign.Center,
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .clickable {
                                                    requestType = RequestType.User
                                                    scope
                                                            .launch { sheetState.hide() }
                                                            .invokeOnCompletion { expanded = false }
                                                }
                                                .padding(all = 10.dp),
                                color =
                                        if (requestType == RequestType.User)
                                                MaterialTheme.colorScheme.primary
                                        else Color.Unspecified,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                text = RequestType.User.toName()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.width(2.dp))
        }

        when (val smartCastData = state) {
            is Resource.Success ->
                    if (smartCastData.data.error != null) {
                        Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AurCardError(
                                        modifier = Modifier.fillMaxWidth(),
                                        err = smartCastData.data.error,
                                        textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                                modifier = Modifier.padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(smartCastData.data.results) { message ->
                                AurResultCard(
                                        data = message,
                                        modifier =
                                                Modifier.padding(all = 2.dp)
                                                        .fillMaxSize()
                                                        .clickable {
                                                            vm.insertHistory(message)
                                                            detailModel.setData(message)
                                                            navController.navigate("DetailPage")
                                                        }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(4.dp)) }
                            item {
                                Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = "Above is all I can provide to you",
                                        textAlign = TextAlign.Center,
                                        fontSize = 15.sp,
                                )
                            }
                        }
                    }
            is Resource.Failure ->
                    Text(
                            modifier = Modifier.fillMaxSize(),
                            text = smartCastData.message,
                            textAlign = TextAlign.Center
                    )
            Resource.Begin ->
                    if (dataList.value.isEmpty()) {
                        Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                    modifier = Modifier.fillMaxWidth(),
                                    text = "Today is a good day, doesn't it?",
                                    textAlign = TextAlign.Center,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        LazyColumn(
                                modifier = Modifier.padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(dataList.value) { message ->
                                AurResultCard(
                                        data = message,
                                        modifier =
                                                Modifier.padding(all = 2.dp)
                                                        .fillMaxSize()
                                                        .clickable {
                                                            detailModel.setData(message)
                                                            navController.navigate("DetailPage")
                                                        }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(4.dp)) }
                            item {
                                Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = "Today is a good day, doesn't it?",
                                        textAlign = TextAlign.Center,
                                        fontSize = 15.sp,
                                )
                            }
                        }
                    }
            else ->
                    Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                trackColor = MaterialTheme.colorScheme.secondary,
                        )
                    }
        }
    }
}

@Preview
@Composable
fun FloatActionBtn() {
    ExtendedFloatingActionButton(
            onClick = { println("ss") },
            icon = { Icon(Icons.Filled.Favorite, "Localized description") },
            text = { Text(text = "Extended FAB") },
    )
}

fun String.toIcon(): Int {
    when (this) {
        "search" -> return R.drawable.search
        else -> return R.drawable.about
    }
}

@Composable
fun PopAndPipBottomBar(list: List<String>, navController: NavController) {
    var selectedItem by remember { mutableIntStateOf(0) }
    val callback =
            NavController.OnDestinationChangedListener end@{ _, destination, _ ->
                if (destination.route == null) return@end
                val index = list.withIndex().first { destination.route == it.value }.index
                if (index >= 0) selectedItem = index
            }
    navController.addOnDestinationChangedListener(callback)

    NavigationBar {
        list.forEachIndexed { index, item ->
            NavigationBarItem(
                    icon = {
                        Icon(
                                modifier = Modifier.width(30.dp).height(30.dp),
                                painter = painterResource(id = item.toIcon()),
                                contentDescription = item
                        )
                    },
                    label = { Text(item) },
                    selected = selectedItem == index,
                    onClick = {
                        selectedItem = index
                        navController.navigate(item)
                    }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(dp: PaddingValues? = null) {
    var showDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val remoteUrl = "https://github.com/Decodetalkers/learningandroid"
    val modifier =
            Modifier.fillMaxSize().let done@{
                if (dp == null) return@done it
                it.padding(dp)
            }
    Scaffold(
            topBar = {
                TopAppBar(
                        title = {
                            Text(
                                    text = "About",
                                    modifier = Modifier.fillMaxWidth().padding(all = 10.dp),
                                    fontSize = 30.sp,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right
                            )
                        }
                )
            },
            modifier = modifier,
    ) { padding ->
        Column(
                modifier = Modifier.padding(padding).fillMaxHeight().padding(all = 30.dp),
        ) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                    modifier = Modifier.fillMaxWidth().padding(start = 10.dp),
                    text = "Project PopAndPip -> An Aur Searcher",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                    modifier =
                            Modifier.clickable { uriHandler.openUri(remoteUrl) }
                                    .fillMaxWidth()
                                    .padding(10.dp)
            ) {
                Text(text = "Github", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                        text = "View the source code",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                    modifier =
                            Modifier.clickable { showDialog = true }.fillMaxWidth().padding(10.dp)
            ) {
                Text(text = "License", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "MIT", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
    if (showDialog) {
        DialogLicense { showDialog = false }
    }
}

const val MITLICENSE =
        """
MIT License
Copyright (c) 2023 Decodetalkers

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""

@Composable
fun DialogLicense(
        onDismissRequest: () -> Unit,
) {
    Dialog(onDismissRequest = { onDismissRequest() }) {
        Card(
                modifier = Modifier.fillMaxWidth().height(475.dp).padding(16.dp),
                shape = RoundedCornerShape(16.dp),
        ) {
            LazyColumn {
                item {
                    Text(
                            text = MITLICENSE,
                            modifier = Modifier.padding(16.dp),
                    )
                }
                item {
                    Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                                onClick = { onDismissRequest() },
                                modifier = Modifier.padding(8.dp),
                        ) { Text("Ok") }
                    }
                }
            }
        }
    }
}
