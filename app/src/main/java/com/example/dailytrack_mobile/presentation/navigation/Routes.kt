package com.example.dailytrack_mobile.presentation.navigation

sealed class Routes(val route: String) {
    object Auth : Routes("auth")
    object Home : Routes("home")
    object Money : Routes("money")
    object Activities : Routes("activities")
    object Investments : Routes("investments")
    object Sabdekho : Routes("sabdekho")
    object Settings : Routes("settings")
    
    // Forms
    object AddMoney : Routes("add_money")
    object AddActivity : Routes("add_activity")
    object AddMovie : Routes("add_movie")
    object AddAsset : Routes("add_asset")
    object AddInvestment : Routes("add_investment")
    object SyncBroker : Routes("sync_broker")
    object Analytics : Routes("analytics")
    object Budgets : Routes("budgets")
}
