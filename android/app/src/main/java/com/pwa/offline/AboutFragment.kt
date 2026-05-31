package com.pwa.offline

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment

class AboutFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_about, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val versionName = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0)
            .versionName
            .orEmpty()
        view.findViewById<TextView>(R.id.aboutVersionText).text =
            getString(R.string.about_version_value, versionName)
        view.findViewById<TextView>(R.id.aboutDatabaseVersionText).text =
            getString(R.string.about_database_version_value, AppDatabaseHelper.databaseVersion())

        view.findViewById<Button>(R.id.aboutRepoButton).setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.about_repo_url)))
            )
        }
    }
}
