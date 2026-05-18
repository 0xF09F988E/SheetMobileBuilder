package com.pwa.offline

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.Toolbar
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var drawerRecyclerView: RecyclerView
    private lateinit var drawerAdapter: ModuleAdapter
    private lateinit var navHostFragment: NavHostFragment
    private lateinit var navigationLoadingOverlay: View
    private lateinit var navigationLoadingText: TextView
    private lateinit var toolbar: MaterialToolbar
    private var pendingDestinationId: Int? = null

    private val modules = ModuleRegistry.defaultModules
    private val moduleTitleByDestinationId = modules.associate { it.destinationId to it.title }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val topAppBarContainer = findViewById<AppBarLayout>(R.id.topAppBarContainer)
        toolbar = findViewById(R.id.topToolbar)
        val navHostContainer = findViewById<View>(R.id.navHostContainer)
        val drawerContentContainer = findViewById<LinearLayout>(R.id.drawerContentContainer)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        drawerLayout = findViewById(R.id.drawerLayout)
        drawerRecyclerView = findViewById(R.id.drawerRecyclerView)
        navigationLoadingOverlay = findViewById(R.id.navigationLoadingOverlay)
        navigationLoadingText = findViewById(R.id.navigationLoadingText)

        navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostContainer) as NavHostFragment
        val navController = navHostFragment.navController
        navController.addOnDestinationChangedListener { _, destination, _ ->
            renderAppBar(destination.id, destination.label?.toString())
            navHostContainer.post { hideNavigationOverlay() }
        }
        renderAppBar(
            navController.currentDestination?.id,
            navController.currentDestination?.label?.toString()
        )

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar as Toolbar,
            R.string.drawer_open,
            R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                val destinationId = pendingDestinationId ?: return
                pendingDestinationId = null
                navigateTo(destinationId)
            }
        })
        toggle.syncState()

        drawerAdapter = ModuleAdapter(modules) { module ->
            val currentId = navController.currentDestination?.id
            if (currentId == module.destinationId) {
                drawerLayout.closeDrawer(GravityCompat.START)
                return@ModuleAdapter
            }

            pendingDestinationId = module.destinationId
            showNavigationOverlay(module.title)
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        drawerRecyclerView.layoutManager = LinearLayoutManager(this)
        drawerRecyclerView.adapter = drawerAdapter

        val appBarLeftPadding = topAppBarContainer.paddingLeft
        val appBarRightPadding = topAppBarContainer.paddingRight
        val appBarTopPadding = topAppBarContainer.paddingTop
        val navBottomPadding = navHostContainer.paddingBottom
        val navLeftPadding = navHostContainer.paddingLeft
        val navRightPadding = navHostContainer.paddingRight
        val drawerTopPadding = drawerContentContainer.paddingTop
        val drawerBottomPadding = drawerContentContainer.paddingBottom
        val drawerLeftPadding = drawerContentContainer.paddingLeft
        val drawerRightPadding = drawerContentContainer.paddingRight

        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            applyInsets(
                topAppBarContainer = topAppBarContainer,
                appBarLeftPadding = appBarLeftPadding,
                appBarRightPadding = appBarRightPadding,
                appBarTopPadding = appBarTopPadding,
                navHostContainer = navHostContainer,
                navBottomPadding = navBottomPadding,
                navLeftPadding = navLeftPadding,
                navRightPadding = navRightPadding,
                drawerContentContainer = drawerContentContainer,
                drawerTopPadding = drawerTopPadding,
                drawerBottomPadding = drawerBottomPadding,
                drawerLeftPadding = drawerLeftPadding,
                drawerRightPadding = drawerRightPadding,
                systemBars = systemBars
            )
            windowInsets
        }

        ViewCompat.requestApplyInsets(drawerLayout)
    }

    private fun applyInsets(
        topAppBarContainer: AppBarLayout,
        appBarLeftPadding: Int,
        appBarRightPadding: Int,
        appBarTopPadding: Int,
        navHostContainer: View,
        navBottomPadding: Int,
        navLeftPadding: Int,
        navRightPadding: Int,
        drawerContentContainer: LinearLayout,
        drawerTopPadding: Int,
        drawerBottomPadding: Int,
        drawerLeftPadding: Int,
        drawerRightPadding: Int,
        systemBars: Insets
    ) {
        topAppBarContainer.updatePadding(
            top = appBarTopPadding + systemBars.top,
            left = appBarLeftPadding + systemBars.left,
            right = appBarRightPadding + systemBars.right
        )
        navHostContainer.updatePadding(
            left = navLeftPadding + systemBars.left,
            right = navRightPadding + systemBars.right,
            bottom = navBottomPadding + systemBars.bottom
        )
        drawerContentContainer.updatePadding(
            left = drawerLeftPadding + systemBars.left,
            right = drawerRightPadding + systemBars.right,
            top = drawerTopPadding + systemBars.top,
            bottom = drawerBottomPadding + systemBars.bottom
        )
    }

    private fun navigateTo(destinationId: Int) {
        val navController = navHostFragment.navController
        val currentId = navController.currentDestination?.id
        if (currentId == destinationId) {
            hideNavigationOverlay()
            return
        }

        navController.navigate(destinationId)
    }

    private fun showNavigationOverlay(moduleTitle: String) {
        navigationLoadingText.text = getString(R.string.navigation_loading, moduleTitle)
        if (navigationLoadingOverlay.visibility != View.VISIBLE) {
            navigationLoadingOverlay.alpha = 0f
            navigationLoadingOverlay.visibility = View.VISIBLE
        }
        navigationLoadingOverlay.animate().cancel()
        navigationLoadingOverlay.animate()
            .alpha(1f)
            .setDuration(70L)
            .start()
    }

    private fun hideNavigationOverlay() {
        if (navigationLoadingOverlay.visibility != View.VISIBLE) {
            return
        }
        navigationLoadingOverlay.animate().cancel()
        navigationLoadingOverlay.animate()
            .alpha(0f)
            .setDuration(90L)
            .withEndAction {
                navigationLoadingOverlay.visibility = View.GONE
            }
            .start()
    }

    private fun renderAppBar(destinationId: Int?, destinationLabel: String?) {
        val title = destinationLabel
            ?.takeIf { it.isNotBlank() }
            ?: moduleTitleByDestinationId[destinationId]
            ?: getString(R.string.app_name)
        supportActionBar?.title = title
        toolbar.title = title
    }
}
