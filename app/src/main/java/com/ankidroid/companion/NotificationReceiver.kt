package com.ankidroid.companion

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.i("Notifications", "onReceive called: ${intent?.action}")
        if (context == null)
            return

        // Extract card data passed from notification extras
        val cardQ = intent?.getStringExtra("card_q")
        val cardA = intent?.getStringExtra("card_a")

        when (intent?.action) {
            // Notification button presses
            "ACTION_BUTTON_1" -> respondCard(context, AnkiDroidHelper.EASE_1, cardQ, cardA)
            "ACTION_BUTTON_2" -> respondCard(context, AnkiDroidHelper.EASE_2, cardQ, cardA)
            "ACTION_BUTTON_3" -> respondCard(context, AnkiDroidHelper.EASE_3, cardQ, cardA)
            "ACTION_BUTTON_4" -> respondCard(context, AnkiDroidHelper.EASE_4, cardQ, cardA)

            // Tasker asks: just advance to next card (current already answered via notification)
            "com.ankidroid.companion.NEXT_CARD" -> handleNextCard(context)

            // Tasker says: answer current card with ease=X, then advance
            "com.ankidroid.companion.ANSWER_AND_NEXT" -> handleAnswerAndNext(context, intent)
        }
    }

    /**
     * Called when notification button is pressed.
     * Reviews the card, broadcasts to Tasker, and cancels notification.
     * Tasker takes over pacing from here.
     */
    private fun respondCard(context: Context, ease: Int, cardQ: String?, cardA: String?) {
        Log.i("Notifications", "respondCard ease=$ease")
        val mAnkiDroid = AnkiDroidHelper(context)
        val localState = mAnkiDroid.storedState

        if (localState != null) {
            mAnkiDroid.reviewCard(localState.noteID, localState.cardOrd, localState.cardStartTime, ease)
            Log.i("Notifications", "reviewCard done for noteID=${localState.noteID} cardOrd=${localState.cardOrd}")
        } else {
            Log.w("Notifications", "localState is null, cannot review card")
        }

        // Broadcast review result to Tasker
        val deckName = mAnkiDroid.currentDeckName
        sendTaskerBroadcast(context, ease, cardQ, cardA, deckName)

        // Cancel notification — Tasker controls when to advance
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(1)
        Log.i("Notifications", "Notification cancelled, waiting for Tasker")
    }

    /**
     * Tasker wants to advance to the next card without answering the current one.
     * (E.g., when the user already answered via notification button.)
     */
    private fun handleNextCard(context: Context) {
        Log.i("Notifications", "handleNextCard from Tasker")
        val mAnkiDroid = AnkiDroidHelper(context)
        val localState = mAnkiDroid.storedState
        advanceToNextCard(context, mAnkiDroid, localState)
    }

    /**
     * Tasker wants to answer the current card AND advance.
     * Receives ease value from Tasker, writes to AnkiDroid, then fetches next.
     */
    private fun handleAnswerAndNext(context: Context, intent: Intent) {
        val ease = intent.getIntExtra("ease", AnkiDroidHelper.EASE_3)
        Log.i("Notifications", "handleAnswerAndNext ease=$ease from Tasker")

        val mAnkiDroid = AnkiDroidHelper(context)
        val localState = mAnkiDroid.storedState

        if (localState != null && localState.noteID != -1L) {
            mAnkiDroid.reviewCard(localState.noteID, localState.cardOrd, localState.cardStartTime, ease)
            Log.i("Notifications", "reviewCard done via Tasker")
        }

        advanceToNextCard(context, mAnkiDroid, localState)
    }

    /**
     * Fetch the next due card from AnkiDroid, broadcast to Tasker, show notification.
     */
    private fun advanceToNextCard(context: Context, mAnkiDroid: AnkiDroidHelper, localState: StoredState?) {
        val deckId = localState?.deckId ?: -1
        Log.i("Notifications", "advanceToNextCard deckId=$deckId")

        val nextCard = mAnkiDroid.queryCurrentScheduledCard(deckId)
        if (nextCard != null) {
            Log.i("Notifications", "Next card found: ${nextCard.q}")
            mAnkiDroid.storeState(deckId, nextCard)
            val deckName = mAnkiDroid.currentDeckName

            // Broadcast next card data to Tasker
            val bc = Intent("com.ankidroid.companion.NEXT_CARD_DATA")
            bc.putExtra("card_q", nextCard.q)
            bc.putExtra("card_a", nextCard.a)
            bc.putExtra("deck_name", deckName)
            context.sendBroadcast(bc)
            Log.i("Notifications", "NEXT_CARD_DATA broadcast sent")

            // Show notification (standalone fallback)
            Notifications.create().showNotification(context, nextCard, deckName, true)
        } else {
            Log.i("Notifications", "No more cards due")

            // Mark state as empty
            val emptyCard = CardInfo()
            emptyCard.cardOrd = -1
            emptyCard.noteID = -1L
            mAnkiDroid.storeState(deckId, emptyCard)

            // Tell Tasker we're done
            val bc = Intent("com.ankidroid.companion.NO_MORE_CARDS")
            context.sendBroadcast(bc)
            Log.i("Notifications", "NO_MORE_CARDS broadcast sent")

            Notifications.create().showNotification(context, null, "", true)
        }
    }

    /**
     * Send review result to Tasker.
     * Tasker listens for: com.ankidroid.companion.TASKER_ACTION
     * Variables: %ease, %card_q, %card_a, %deck_name
     */
    private fun sendTaskerBroadcast(context: Context, ease: Int, cardQ: String?, cardA: String?, deckName: String?) {
        Log.i("Notifications", "Sending TASKER_ACTION: ease=$ease q=$cardQ")
        val intent = Intent("com.ankidroid.companion.TASKER_ACTION")
        intent.putExtra("ease", ease)
        intent.putExtra("card_q", cardQ ?: "")
        intent.putExtra("card_a", cardA ?: "")
        intent.putExtra("deck_name", deckName ?: "")
        context.sendBroadcast(intent)
    }
}