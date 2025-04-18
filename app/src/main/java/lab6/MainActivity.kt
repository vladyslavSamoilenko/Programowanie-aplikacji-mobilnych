package lab6

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import lab6.ui.theme.Lab6Theme
import com.example.lab2.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import lab6.data.AppContainer
import lab6.data.LocalDateConverter
import lab6.data.Priority
import lab6.data.TodoApplication
import lab6.data.TodoTask

const val notificationID = 121
const val channelID = "Lab06 channel"
const val titleExtra = "title"
const val messageExtra = "message"

class MainActivity : ComponentActivity() {
    var soonestTask: TodoTask? = null
    var soonestAlarmIntent: PendingIntent? = null

    lateinit var preferences: SharedPreferences

    var notifDaysBefore: Long = 1
    var notifHoursBefore: Long = 0
    var notifHourInterval: Long = 4

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()
        container = (this.application as TodoApplication).container

        preferences = getPreferences(MODE_PRIVATE)

        notifDaysBefore = preferences.getLong("days", 1)
        notifHoursBefore = preferences.getLong("hours", 0)
        notifHourInterval = preferences.getLong("interval", 4)

        rescheduleAlarmIfNeeded()

        enableEdgeToEdge()
        setContent {
            Lab6Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    fun rescheduleAlarmIfNeeded(): Unit {
        val activity = this

        lifecycleScope.launch {
            container.todoTaskRepository.getAllAsStream().collect { collector ->
                if (collector.isEmpty()) return@collect

                val soonestTask = collector.minBy { task -> task.deadline }
                if (activity.soonestTask == null || soonestTask.deadline < activity.soonestTask!!.deadline) return@collect

                activity.soonestTask = soonestTask

                val deadline = activity.soonestTask!!.deadline.minusDays(notifDaysBefore)
                val deadlineMillis =
                    LocalDateConverter.toMillis(deadline) - notifHoursBefore * AlarmManager.INTERVAL_HOUR

                val alarmManager = getSystemService(AlarmManager::class.java)
                if (activity.soonestAlarmIntent != null)
                    alarmManager.cancel(activity.soonestAlarmIntent!!)

                scheduleAlarm(deadlineMillis)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @OptIn(ExperimentalPermissionsApi::class)
    @Composable
    fun MainScreen() {
        val navController = rememberNavController()
        val postNotificationPermission =
            rememberPermissionState(permission = android.Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(key1 = true) {
            if (!postNotificationPermission.status.isGranted) {
                postNotificationPermission.launchPermissionRequest()
            }
        }
        val activity = this
        NavHost(navController = navController, startDestination = "list") {
            composable("list") { ListScreen(navController = navController) }
            composable("form") {
                FormScreen(
                    navController = navController, mainActivity = activity
                )
            }
            composable("settings") {
                SettingsScreen(navController = navController)
            }
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun MainScreenPreview() {
        Lab6Theme {
            MainScreen(
            )
        }
    }

    @Composable
    fun SettingsScreen(navController: NavController) {
        Scaffold(
            topBar = {
                AppTopBar(
                    navController = navController,
                    title = "Settings",
                    showBackIcon = true,
                    route = "settings",
                )
            },
            content = { pad ->
                Column(modifier = Modifier.padding(pad)) {
                    var days by remember { mutableLongStateOf(notifDaysBefore) }
                    var hours by remember { mutableLongStateOf(notifHoursBefore) }
                    var interval by remember { mutableLongStateOf(notifHourInterval) }

                    OutlinedTextField(
                        value = days.toString(),
                        onValueChange = {
                            if (it.isEmpty()) days = 0
                            else days = it.toLong()

                            with (preferences.edit()) {
                                putLong("days", days)
                                apply()
                            }
                        },
                        label = { Text("Days before deadline") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    OutlinedTextField(
                        value = hours.toString(),
                        onValueChange = {
                            if (it.isEmpty()) hours = 0
                            else hours = it.toLong()

                            with (preferences.edit()) {
                                putLong("hours", hours)
                                apply()
                            }
                        },
                        label = { Text("Hours before deadline") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )

                    OutlinedTextField(
                        value = interval.toString(),
                        onValueChange = {
                            if (it.isEmpty()) interval = 0
                            else interval = it.toLong()

                            with (preferences.edit()) {
                                putLong("interval", interval)
                                apply()
                            }
                        },
                        label = { Text("Hour notification interval") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        )

    }

    fun scheduleAlarm(time: Long) {
        val intent = Intent(applicationContext, NotificationBroadcastReceiver::class.java)
        intent.putExtra(titleExtra, "Deadline")
        intent.putExtra(messageExtra, "Zbliża się termin zakończenia zadania")

        val pendingIntent = PendingIntent.getBroadcast(
            applicationContext, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager

        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME,
            time,
            AlarmManager.INTERVAL_HOUR * notifHourInterval,
            pendingIntent
        )

        soonestAlarmIntent = pendingIntent
    }

    private fun createNotificationChannel() {
        val name = "Lab06 channel"
        val descriptionText = "Lab06 is channel for notifications for approaching tasks."
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(channelID, name, importance).apply {
            description = descriptionText
        }
        val notificationManager: NotificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        lateinit var container: AppContainer
    }
}

class NotificationBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val notification = NotificationCompat.Builder(context, channelID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(intent?.getStringExtra(titleExtra))
            .setContentText(intent?.getStringExtra(messageExtra)).build()
        val manager =
            context.getSystemService(NotificationManager::class.java) as NotificationManager
        manager.notify(notificationID, notification)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!", modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    Lab6Theme {
        Greeting("Android")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    navController: NavController,
    viewModel: ListViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val listUiState by viewModel.listUiState.collectAsState()

    Scaffold(floatingActionButton = {
        FloatingActionButton(shape = CircleShape, content = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add task",
                modifier = Modifier.scale(1.5f)
            )
        }, onClick = {
            navController.navigate("form")
        })
    }, topBar = {
        AppTopBar(
            navController = navController, title = "List", showBackIcon = false, route = "list"
        )
    }, content = {
        LazyColumn(modifier = Modifier.padding(it)) {
            items(listUiState.items.size) { item ->
                ListItem(item = listUiState.items[item])
            }
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoTaskInputForm(
    item: TodoTaskForm,
    modifier: Modifier = Modifier,
    onValueChange: (TodoTaskForm) -> Unit = {},
    enabled: Boolean = true
) {
    OutlinedTextField(value = item.title, onValueChange = {
        onValueChange(item.copy(title = it))
    }, label = { Text("Title") })
    val datePickerState = rememberDatePickerState(
        initialDisplayMode = DisplayMode.Picker,
        yearRange = IntRange(2000, 2030),
        initialSelectedDateMillis = item.deadline,
    )
    var showDialog by remember {
        mutableStateOf(false)
    }
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = {
                showDialog = true
            }),
        text = "Date",
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.headlineMedium
    )
    if (showDialog) {
        DatePickerDialog(onDismissRequest = {
            showDialog = false
        }, confirmButton = {
            Button(onClick = {
                showDialog = false
                onValueChange(item.copy(deadline = datePickerState.selectedDateMillis!!))
            }) {
                Text("Pick")
            }
        }) {
            DatePicker(state = datePickerState, showModeToggle = true)
        }
    }

    var selectedPriority by remember { mutableStateOf(Priority.Low) }

    Column(
        horizontalAlignment = Alignment.Start
    ) {
        for (prio in Priority.entries) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedPriority == prio, onClick = {
                        selectedPriority =
                            prio; onValueChange(item.copy(priority = selectedPriority.name))
                    })
                Text(
                    text = prio.name, modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    var isDone by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = isDone,
            onCheckedChange = {
                isDone = !isDone
                onValueChange(item.copy(isDone = isDone))
            },
        )

        Text("Is done")
    }
}

@Composable
fun TodoTaskInputBody(
    todoUiState: TodoTaskUiState,
    onItemValueChange: (TodoTaskForm) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TodoTaskInputForm(
            item = todoUiState.todoTask, onValueChange = onItemValueChange, modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(
    navController: NavController,
    mainActivity: MainActivity,
    viewModel: FormViewModel = viewModel(factory = FormViewModelProvider.Factory)
) {
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            AppTopBar(
                navController = navController,
                title = "Form",
                showBackIcon = true,
                route = "form",
                onSaveClick = {
                    coroutineScope.launch {
                        viewModel.save()
                        mainActivity.rescheduleAlarmIfNeeded()
                        navController.navigate("list")
                    }
                })
        }) {
        TodoTaskInputBody(
            todoUiState = viewModel.todoTaskUiState,
            onItemValueChange = viewModel::updateUiState,
            modifier = Modifier.padding(it)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    navController: NavController,
    title: String,
    showBackIcon: Boolean,
    route: String,
    onSaveClick: () -> Unit = {}
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary
        ), title = { Text(text = title) }, navigationIcon = {
            if (showBackIcon) {
                IconButton(onClick = { navController.navigate("list") }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack, contentDescription = "Back"
                    )
                }
            }
        }, actions = {
            if (route == "form") {
                OutlinedButton(
                    onClick = {
                        onSaveClick()
                    }) {
                    Text(
                        text = "Zapisz", fontSize = 18.sp
                    )
                }
            } else if (route == "list") {
                IconButton(onClick = {
                    navController.navigate("settings")
                }) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "")
                }
            }
        })
}

@Composable
fun ListItem(item: TodoTask, modifier: Modifier = Modifier) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(120.dp)
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Row(Modifier.padding(horizontal = 1.dp)) {
            Column(Modifier.padding(horizontal = 3.dp)) {
                Text(text = item.title)
                Text(text = "Priority: ${item.priority}")
            }
            Column(Modifier.padding(horizontal = 3.dp)) {
                var iconTint: Color
                var iconResource: Int
                if (item.isDone) {
                    iconResource = R.drawable.baseline_done_24
                    iconTint = Color(0, 255, 0, 255)
                } else {
                    iconResource = R.drawable.baseline_not_done_24
                    iconTint = Color(255, 0, 0, 255)
                }

                Icon(
                    painter = painterResource(iconResource),
                    contentDescription = "isDone",
                    tint = iconTint
                )
            }
            Column(Modifier.padding(horizontal = 3.dp)) {
                Text(text = "Deadline: ${item.deadline}")
            }
        }
    }
}
