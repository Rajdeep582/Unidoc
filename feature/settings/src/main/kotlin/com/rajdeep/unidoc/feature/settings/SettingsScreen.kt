package com.rajdeep.unidoc.feature.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Colorize
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rajdeep.unidoc.core.model.OcrEngine
import com.rajdeep.unidoc.core.model.OcrLanguage
import com.rajdeep.unidoc.core.model.QualityPreset
import com.rajdeep.unidoc.core.model.ThemeMode
import com.rajdeep.unidoc.core.model.UserSettings
import com.rajdeep.unidoc.core.ui.brand.BrandLogo
import com.rajdeep.unidoc.core.ui.components.CloudBadge
import com.rajdeep.unidoc.core.ui.components.NavigationRow
import com.rajdeep.unidoc.core.ui.components.SegmentedControl
import com.rajdeep.unidoc.core.ui.components.SettingsDivider
import com.rajdeep.unidoc.core.ui.components.SettingsGroup
import com.rajdeep.unidoc.core.ui.components.SwitchRow
import com.rajdeep.unidoc.core.ui.components.UnidocBottomSheet
import com.rajdeep.unidoc.core.ui.components.UnidocTopBar
import com.rajdeep.unidoc.core.ui.theme.Dimens
import com.rajdeep.unidoc.core.ui.theme.UnidocTheme
import com.rajdeep.unidoc.feature.settings.legal.LegalDocument
import com.rajdeep.unidoc.feature.settings.legal.LegalDocuments
import com.rajdeep.unidoc.feature.settings.legal.LegalInfo
import com.rajdeep.unidoc.feature.settings.legal.OpenSourceNotices

@Composable
fun SettingsRouteScreen(
    versionName: String,
    onBack: () -> Unit,
    onOpenGallery: (() -> Unit)?,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val probe by viewModel.probeState.collectAsStateWithLifecycle()
    settings?.let { SettingsScreen(it, viewModel::onAction, versionName, onBack, onOpenGallery, probe = probe) }
}

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onAction: (SettingsAction) -> Unit,
    versionName: String,
    onBack: () -> Unit,
    onOpenGallery: (() -> Unit)?,
    modifier: Modifier = Modifier,
    probe: ProbeState = ProbeState.Idle
) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 8
        }
    }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        UnidocTopBar(title = "Settings", onBack = onBack, scrolled = scrolled)
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 48.dp),
            modifier = Modifier.navigationBarsPadding()
        ) {
            item {
                SettingsGroup("Appearance") {
                    Column(Modifier.padding(Dimens.SpaceL)) {
                        Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(Dimens.SpaceS))
                        SegmentedControl(listOf("System", "Light", "Dark"), settings.themeMode.ordinal, {
                            onAction(SettingsAction.Theme(ThemeMode.entries[it]))
                        })
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SettingsDivider()
                        SwitchRow(
                            title = "Wallpaper colors",
                            subtitle = "Material You surfaces; brand gradient stays",
                            icon = Icons.Rounded.Colorize,
                            checked = settings.dynamicColor,
                            onCheckedChange = { onAction(SettingsAction.DynamicColor(it)) }
                        )
                    }
                }
            }
            item {
                SettingsGroup("Scanning") {
                    SwitchRow(
                        title = "Auto-capture",
                        subtitle = "Shoot when the page is steady and sharp",
                        icon = Icons.Rounded.AutoMode,
                        checked = settings.autoCapture,
                        onCheckedChange = { onAction(SettingsAction.AutoCapture(it)) }
                    )
                    SettingsDivider()
                    Column(Modifier.padding(Dimens.SpaceL)) {
                        Text("Default quality", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Balanced keeps text crisp at a fraction of the size",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(Dimens.SpaceS))
                        SegmentedControl(listOf("High", "Balanced", "Small"), settings.defaultQuality.ordinal, {
                            onAction(SettingsAction.Quality(QualityPreset.entries[it]))
                        })
                    }
                    SettingsDivider()
                    NavigationRow(
                        title = "Text recognition languages",
                        subtitle = settings.ocrLanguages.sortedBy { it.ordinal }.joinToString { it.englishName },
                        icon = Icons.Rounded.Translate,
                        onClick = { sheet = "ocr" }
                    )
                }
            }
            item {
                SettingsGroup("General") {
                    SwitchRow(
                        title = "Haptic feedback",
                        icon = Icons.Rounded.Vibration,
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { onAction(SettingsAction.Haptics(it)) }
                    )
                }
            }
            item {
                SettingsGroup("Privacy & legal") {
                    NavigationRow(
                        "Privacy policy",
                        { sheet = "privacy" },
                        subtitle = "Your scans stay on your phone",
                        icon = Icons.Rounded.PrivacyTip
                    )
                    SettingsDivider()
                    NavigationRow("Terms of use", { sheet = "terms" }, icon = Icons.Rounded.Description)
                    SettingsDivider()
                    NavigationRow("Open-source licenses", { sheet = "licenses" }, icon = Icons.Rounded.Gavel)
                    SettingsDivider()
                    NavigationRow(
                        "Contact & support",
                        {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${LegalInfo.SUPPORT_EMAIL}"))
                                        .putExtra(Intent.EXTRA_SUBJECT, "Unidoc $versionName — feedback")
                                )
                            }
                        },
                        subtitle = "Email the developer",
                        icon = Icons.Rounded.Email
                    )
                }
            }
            item {
                SettingsGroup("About") {
                    Row(
                        Modifier.padding(Dimens.SpaceL),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceL)
                    ) {
                        BrandLogo(size = 52.dp)
                        Column {
                            Text("Unidoc", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Version $versionName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (onOpenGallery != null) {
                        SettingsDivider()
                        NavigationRow(
                            "Design gallery",
                            onOpenGallery,
                            subtitle = "Debug build · every screen and state",
                            icon = Icons.Rounded.Dashboard
                        )
                    }
                }
            }
        }
    }

    when (sheet) {
        "ocr" -> OcrLanguagesSheet(settings.ocrLanguages, {
            onAction(SettingsAction.OcrLanguages(it))
        }, { sheet = null })
        "licenses" -> LicensesSheet { sheet = null }
        "privacy" -> LegalSheet(LegalDocuments.privacyPolicy) { sheet = null }
        "terms" -> LegalSheet(LegalDocuments.termsOfUse) { sheet = null }
    }
}

@Composable
private fun OcrLanguagesSheet(selected: Set<OcrLanguage>, onChange: (Set<OcrLanguage>) -> Unit, onDismiss: () -> Unit) {
    UnidocBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                Text(
                    "Text recognition",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = Dimens.Gutter)
                )
                Text(
                    "English is always on. Everything runs on your phone. More Indian languages are coming soon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceS)
                )
            }
            OcrEngine.entries.forEach { engine ->
                item {
                    Text(
                        if (engine == OcrEngine.ON_DEVICE) "ON YOUR PHONE" else "COMING SOON",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = Dimens.Gutter,
                            top = Dimens.SpaceL,
                            bottom = Dimens.SpaceXs
                        )
                    )
                }
                items(OcrLanguage.entries.filter { it.engine == engine }) { lang ->
                    val on = lang in selected
                    val soon = lang.engine == OcrEngine.CLOUD
                    val locked = lang == OcrLanguage.ENGLISH || soon
                    LanguageRow(lang, on && !soon, locked, soon) { onChange(if (on) selected - lang else selected + lang) }
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(lang: OcrLanguage, on: Boolean, locked: Boolean, soon: Boolean, onToggle: () -> Unit) {
    val colors = UnidocTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !locked, role = Role.Checkbox, onClick = onToggle)
            .alpha(if (soon) 0.5f else 1f)
            .padding(horizontal = Dimens.Gutter, vertical = Dimens.SpaceM),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(lang.nativeName, style = MaterialTheme.typography.titleMedium)
            Text(
                lang.englishName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (soon) {
            CloudBadge(label = "Soon", icon = Icons.Rounded.Schedule)
            return@Row
        }
        Box(
            Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (on) {
                        Modifier.background(
                            colors.brandBrush
                        )
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (on) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun LicensesSheet(onDismiss: () -> Unit) {
    UnidocBottomSheet(onDismissRequest = onDismiss, dragToDismiss = false) {
        SheetTitle("Open-source licenses", onDismiss)
        Column(
            Modifier
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.Gutter)
                .padding(bottom = 40.dp)
        ) {
            OpenSourceNotices.forEach { (name, license) ->
                Row(Modifier.padding(vertical = Dimens.SpaceS), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(Modifier.padding(start = Dimens.SpaceM)) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            license,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegalSheet(document: LegalDocument, onDismiss: () -> Unit) {
    UnidocBottomSheet(onDismissRequest = onDismiss, dragToDismiss = false) {
        SheetTitle(document.title, onDismiss)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.Gutter)
                .padding(bottom = 40.dp)
        ) {
            Text(document.intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            document.sections.forEach { section ->
                Spacer(Modifier.height(Dimens.SpaceL))
                Text(section.heading, style = MaterialTheme.typography.titleSmall)
                section.paragraphs.forEach {
                    Spacer(Modifier.height(Dimens.SpaceXs))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Fixed title row with a close button (these sheets don't close by dragging, so scrolling never dismisses them). */
@Composable
private fun SheetTitle(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Dimens.Gutter, end = Dimens.SpaceS, bottom = Dimens.SpaceS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
    }
}
