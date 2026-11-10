package com.dhalam.ap

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun WhatsAppBotPanel(
    botNumber: String = BuildConfig.WHATSAPP_BOT_NUMBER,
    openChat: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val number = remember(botNumber) { whatsappContactNumber(botNumber) }
    var failure by remember { mutableStateOf<String?>(null) }
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(painterResource(R.drawable.ms_chat), contentDescription = null,
                modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Text(trText("Dhalam on WhatsApp"), style = MaterialTheme.typography.titleLarge)
            Text(trText("Send CHECK: with a message, or caption a photo CHECK."),
                style = MaterialTheme.typography.bodyMedium)
            Text(trText("Checks go to Google AI. Remove private details."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (number != null) Text("+$number", style = MaterialTheme.typography.bodyMedium)
            else Text(trText("Bot contact unavailable."), style = MaterialTheme.typography.bodySmall)
            Button(enabled = number != null, onClick = {
                val contact = number ?: return@Button
                val url = whatsappBotChatUrl(contact)
                failure = null
                try {
                    if (openChat != null) openChat(url)
                    else {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.whatsapp")) }
                        catch (_: ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        }
                    }
                } catch (_: ActivityNotFoundException) { failure = "Install WhatsApp or open the link in a browser." }
                  catch (_: SecurityException) { failure = "Could not open WhatsApp." }
            }) { Text(trText("Open WhatsApp bot")) }
            TextButton(enabled = number != null, onClick = {
                try {
                    context.startActivity(Intent(ContactsContract.Intents.Insert.ACTION)
                        .setType(ContactsContract.RawContacts.CONTENT_TYPE)
                        .putExtra(ContactsContract.Intents.Insert.NAME, "Dhalam")
                        .putExtra(ContactsContract.Intents.Insert.PHONE, "+$number"))
                } catch (_: ActivityNotFoundException) { failure = "No contacts app available." }
                  catch (_: SecurityException) { failure = "Could not open contacts." }
            }) { Text(trText("Save bot contact")) }
            HorizontalDivider()
            Text(trText("Live call listening is unavailable."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            failure?.let { Text(trText(it), color = MaterialTheme.colorScheme.error) }
        }
    }
}
