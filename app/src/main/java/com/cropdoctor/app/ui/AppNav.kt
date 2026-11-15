package com.cropdoctor.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cropdoctor.app.ui.ask.AskScreen
import com.cropdoctor.app.ui.fields.FieldDetailScreen
import com.cropdoctor.app.ui.fields.FieldsScreen
import com.cropdoctor.app.ui.home.HomeScreen
import com.cropdoctor.app.ui.label.LabelScreen
import com.cropdoctor.app.ui.result.ResultScreen
import com.cropdoctor.app.ui.scan.ScanScreen
import com.cropdoctor.app.ui.settings.SettingsScreen

@Composable
fun AppNav(pendingScan: String?, onPendingHandled: () -> Unit) {
    val nav = rememberNavController()

    LaunchedEffect(pendingScan) {
        if (pendingScan != null) {
            nav.navigate("result/$pendingScan")
            onPendingHandled()
        }
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onScan = { nav.navigate("scan") },
                onFields = { nav.navigate("fields") },
                onLabel = { nav.navigate("label") },
                onAsk = { nav.navigate("ask?mic=false") },
                onVoice = { nav.navigate("ask?mic=true") },
                onSettings = { nav.navigate("settings") },
                onOpenScan = { nav.navigate("result/$it") },
            )
        }
        composable("scan?field={field}", arguments = listOf(navArgument("field") { type = NavType.StringType; nullable = true; defaultValue = null })) {
            ScanScreen(
                onBack = { nav.popBackStack() },
                onResult = { id -> nav.navigate("result/$id") { popUpTo("scan?field={field}") { inclusive = true } } },
                startFieldId = it.arguments?.getString("field"),
            )
        }
        composable("result/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            ResultScreen(
                scanId = it.arguments!!.getString("id")!!,
                onBack = { nav.popBackStack() },
                onAsk = { cls -> nav.navigate("ask?mic=false&cls=$cls") },
                onDose = { nav.navigate("label") },
            )
        }
        composable("fields") { FieldsScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate("field/$it") }) }
        composable("field/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            FieldDetailScreen(
                fieldId = it.arguments!!.getString("id")!!,
                onBack = { nav.popBackStack() },
                onScan = { id -> nav.navigate("scan?field=$id") },
                onOpenScan = { id -> nav.navigate("result/$id") },
            )
        }
        composable("label") { LabelScreen(onBack = { nav.popBackStack() }) }
        composable(
            "ask?mic={mic}&cls={cls}",
            arguments = listOf(
                navArgument("mic") { type = NavType.BoolType; defaultValue = false },
                navArgument("cls") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) {
            AskScreen(
                contextClassId = it.arguments?.getString("cls"),
                startWithMic = it.arguments?.getBoolean("mic") ?: false,
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
