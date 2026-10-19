package app.pouchwise.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pouchwise.AppViewModel
import app.pouchwise.R
import app.pouchwise.ui.components.BrandPicker
import app.pouchwise.ui.components.OTHER_BRAND
import app.pouchwise.ui.components.ScreenTitle
import app.pouchwise.ui.components.SoftCard
import app.pouchwise.ui.components.parseDecimal
import app.pouchwise.ui.components.rememberImportFlow
import app.pouchwise.ui.components.resolveBrand

/** First-launch questionnaire: usual pouch and daily count now, and the target two months out. */
@Composable
fun OnboardingScreen(vm: AppViewModel) {
    var brandSel by rememberSaveable { mutableStateOf(OTHER_BRAND) }
    var custom by rememberSaveable { mutableStateOf("") }
    var strengthNow by rememberSaveable { mutableStateOf("") }
    var perDayNow by rememberSaveable { mutableStateOf("") }
    var perDayTarget by rememberSaveable { mutableStateOf("") }
    var strengthTarget by rememberSaveable { mutableStateOf("") }
    val startImport = rememberImportFlow(vm)
    // A quick second tap would create the pouch twice before the screen goes away.
    var starting by remember { mutableStateOf(false) }

    val brand = resolveBrand(brandSel, custom)
    val sNow = parseDecimal(strengthNow)
    val nNow = perDayNow.trim().toIntOrNull()
    val nTarget = perDayTarget.trim().toIntOrNull()
    val sTarget = parseDecimal(strengthTarget.ifBlank { strengthNow })
    val valid = brand.isNotBlank() &&
        sNow != null && sNow > 0 && sNow < 200 &&
        nNow != null && nNow > 0 && nNow < 200 &&
        nTarget != null && nTarget >= 0 && nTarget < 200 &&
        sTarget != null && sTarget > 0 && sTarget < 200

    Scaffold { inner ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenTitle(stringResource(R.string.onboarding_title), stringResource(R.string.onboarding_intro))

            SoftCard {
                Text(stringResource(R.string.onboarding_now), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.onboarding_brand), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                BrandPicker(
                    selected = brandSel,
                    custom = custom,
                    onSelect = { brandSel = it },
                    onCustomChange = { custom = it },
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = strengthNow, onValueChange = { strengthNow = it },
                        label = { Text(stringResource(R.string.onboarding_strength)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = perDayNow, onValueChange = { perDayNow = it },
                        label = { Text(stringResource(R.string.onboarding_per_day)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SoftCard {
                Text(stringResource(R.string.onboarding_target), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = strengthTarget, onValueChange = { strengthTarget = it },
                        label = { Text(stringResource(R.string.onboarding_strength)) },
                        placeholder = { Text(strengthNow) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = perDayTarget, onValueChange = { perDayTarget = it },
                        label = { Text(stringResource(R.string.onboarding_per_day)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Button(
                enabled = valid && !starting,
                onClick = {
                    starting = true
                    vm.completeOnboarding(
                        brand = brand,
                        strengthNow = sNow ?: 0.0,
                        perDayNow = nNow ?: 0,
                        perDayTarget = nTarget ?: 0,
                        strengthTarget = sTarget ?: (sNow ?: 0.0),
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.onboarding_start), style = MaterialTheme.typography.titleMedium) }

            OutlinedButton(onClick = startImport, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(stringResource(R.string.onboarding_restore))
            }

            TextButton(onClick = { vm.skipOnboarding() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
    }
}
