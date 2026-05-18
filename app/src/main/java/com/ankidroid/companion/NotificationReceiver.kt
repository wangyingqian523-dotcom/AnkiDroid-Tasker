package com.ankidroid.companion

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("Notifications", "onReceive called")
        if (context == null)
            return
        Log.i("Notifications", "onReceive called - context is not null")

        // Extract card data passed from notification extras
        val cardQ = intent?.getStringExtra("card_q")
        val cardA = intent?.getStringExtra("card_a")

        when (intent?.action) {
            "ACTION_BUTTON_1" -> respondCard(context, AnkiDroidHelper.EASE_1, cardQ, cardA)
            "ACTION_BUTTON_2" -> respondCard(context, AnkiDroidHelper.EASE_2, cardQ, cardA)
            "ACTION_BUTTON_3" -> respondCard(context, AnkiDroidHelper.EASE_3, cardQ, cardA)
            "ACTION_BUTTON_4" -> respondCard(context, AnkiDroidHelper.EASE_4, cardQ, cardA)
        }
    }

    private fun respondCard(context: Context, ease: Int, cardQ: String?, cardA: String?) {
        Log.i("Notifications", "respondCard called")
        var mAnkiDroid = AnkiDroidHelper(context)
        val localState = mAnkiDroid.storedState

        if (localState != null) {
            Log.i("Notifications", "localState.cardOrd: ${localState.cardOrd}, localState.noteID: ${localState.noteID}")
            mAnkiDroid.reviewCard(localState.noteID, localState.cardOrd, localState.cardStartTime, ease)
        }

        // === Send broadcast to Tasker BEFORE moving to next card ===
        val deckName = mAnkiDroid.getCurrentDeckName()
        sendTaskerBroadcast(context, ease, cardQ, cardA, deckName)

        // Move to next card
        val nextCard = mAnkiDroid.queryCurrentScheduledCard(localState?.deckId ?: -1)
        if (nextCard != null) {
            Log.i("Notifications", "moving to next card.")
            mAnkiDroid.storeState(localState?.deckId ?: -1, nextCard)
            Notifications.create().showNotification(context, nextCard, mAnkiDroid.getCurrentDeckName(), true)
        } else {
            Log.i("Notifications", "no other cards found, showing done notification")
            // No more cards to show.
            val emptyCard = CardInfo()
            emptyCard.cardOrd = -1
            emptyCard.noteID = -1
            mAnkiDroid.storeState(localState?.deckId ?: -1, emptyCard)
            Notifications.create().showNotification(context, null, "", true)
        }
    }

    /**
     * Send a broadcast that Tasker can listen to with an "Intent Received" event profile.
     * Action: com.ankidroid.companion.TASKER_ACTION
     * Tasker variables: %ease, %card_q, %card_a, %deck_name
     */
    private fun sendTaskerBroadcast(context: Context, ease: Int, cardQ: String?, cardA: String?, deckName: String?) {
        Log.i("Notifications", "Sending broadcast to Tasker: ease=$ease")
        val intent = Intent()
        intent.action = "com.ankidroid.companion.TASKER_ACTION"
        intent.putExtra("ease", ease)
        intent.putExtra("card_q", cardQ ?: "")
        intent.putExtra("card_a", cardA ?: "")
        intent.putExtra("deck_name", deckName ?: "")
        context.sendBroadcast(intent)
    }
}