package com.rton.expensebucket.navigation

/**
 * Navigation routes for the app.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AddTransaction : Screen("add_transaction")
    data object EditTransaction : Screen("edit_transaction/{transactionId}") {
        fun createRoute(transactionId: Long) = "edit_transaction/$transactionId"
    }
    data object Statistics : Screen("statistics")
    data object Projects : Screen("projects")
    data object ProjectDetail : Screen("project_detail/{projectId}") {
        fun createRoute(projectId: Long) = "project_detail/$projectId"
    }
    data object Settings : Screen("settings")
    data object Drafts : Screen("drafts")
    data object PaymentMethods : Screen("payment_methods")
    data object CategoryManage : Screen("category_manage")
    data object OcrPreview : Screen("ocr_preview")
    data object ReceiptOcr : Screen("receipt_ocr")
    data object InvoiceOcr : Screen("invoice_ocr")
}
