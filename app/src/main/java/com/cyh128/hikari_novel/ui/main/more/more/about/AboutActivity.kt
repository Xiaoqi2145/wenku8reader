package com.cyh128.hikari_novel.ui.main.more.more.about

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import com.cyh128.hikari_novel.R
import com.cyh128.hikari_novel.base.BaseActivity
import com.cyh128.hikari_novel.data.model.Event
import com.cyh128.hikari_novel.databinding.ActivityAboutBinding
import com.cyh128.hikari_novel.util.Constants
import com.cyh128.hikari_novel.util.openUrl
import com.drake.channel.receiveEvent
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AboutActivity : BaseActivity<ActivityAboutBinding>() {
    private val viewModel by lazy { ViewModelProvider(this)[AboutViewModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSupportActionBar(binding.tbAAbout)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.tbAAbout.setNavigationOnClickListener { finish() }

        receiveEvent<Event>("event_about_activity") { event ->
            when (event) {
                Event.HaveAvailableUpdateEvent -> {
                    MaterialAlertDialogBuilder(this@AboutActivity)
                        .setIcon(R.drawable.ic_release_alert)
                        .setTitle(R.string.update_available)
                        .setMessage(R.string.update_available_tip)
                        .setCancelable(false)
                        .setPositiveButton(R.string.go_to_download) { _, _ ->
                            openUrl(Constants.RELEASES_URL)
                        }
                        .setNegativeButton(R.string.cancel) { _, _ -> }
                        .show()
                    binding.llAAboutUpdate.isClickable = true
                }

                Event.NoAvailableUpdateEvent -> {
                    MaterialAlertDialogBuilder(this@AboutActivity)
                        .setMessage(R.string.you_have_used_in_latest_version)
                        .setCancelable(false)
                        .setPositiveButton(R.string.ok) { _, _ -> }
                        .show()
                    binding.llAAboutUpdate.isClickable = true
                }

                is Event.NetworkErrorEvent -> {
                    // The row is disabled while the check runs. Without this
                    // branch a failed check left it disabled forever, so the
                    // button looked broken until the screen was reopened.
                    binding.llAAboutUpdate.isClickable = true
                    MaterialAlertDialogBuilder(this@AboutActivity)
                        .setIcon(R.drawable.ic_error)
                        .setTitle(R.string.check_update_failure)
                        .setMessage(event.msg)
                        .setPositiveButton(R.string.ok) { _, _ -> }
                        .show()
                }

                else -> {}
            }
        }

        binding.tvAAbout.text =
            applicationContext.packageManager.getPackageInfo(packageName, 0).versionName

        binding.llAAboutDisclaimers.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_contract)
                .setTitle(R.string.disclaimers)
                .setMessage(R.string.disclaimers_message)
                .setPositiveButton(R.string.ok) { _, _ -> }
                .show()
        }

        binding.llAAboutUpdate.setOnClickListener {
            binding.llAAboutUpdate.isClickable = false
            viewModel.checkUpdate()
        }

        binding.bAAboutGithub.setOnClickListener {
            openUrl(Constants.REPOSITORY_URL)
        }
        binding.bAAboutTelegram.setOnClickListener {
            openUrl("https://t.me/+JH2H3VpET7ozMTU9")
        }

        binding.llAAboutHelp.setOnClickListener {
            openUrl(Constants.REPOSITORY_URL)
        }
    }
}
