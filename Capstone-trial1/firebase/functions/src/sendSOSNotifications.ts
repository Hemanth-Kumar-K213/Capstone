import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

/**
 * Triggered whenever an SOS event document is created in Firestore (sosEvents/{eventId}).
 * Identifies all approved Trusted Contacts of the user and sends high-priority FCM push notifications.
 */
export const onSOSEventCreated = functions.firestore
  .document("sosEvents/{eventId}")
  .onCreate(async (snapshot, context) => {
    const eventData = snapshot.data();
    if (!eventData) return;

    const eventId = context.params.eventId;
    const userId = eventData.userId;
    const userName = eventData.userName || "Connected User";
    const lat = eventData.latitude ? String(eventData.latitude) : "";
    const lng = eventData.longitude ? String(eventData.longitude) : "";

    const db = admin.firestore();

    try {
      // 1. Find all approved relationships where userId == eventData.userId
      const relationshipsSnapshot = await db
        .collection("relationships")
        .where("userId", "==", userId)
        .where("permissionState", "==", "APPROVED")
        .get();

      if (relationshipsSnapshot.empty) {
        console.log(`No approved trusted contacts found for user ${userId}`);
        return;
      }

      const tcIds = relationshipsSnapshot.docs.map((doc) => doc.data().trustedContactId);

      // 2. Fetch FCM tokens for each approved Trusted Contact
      const tokens: string[] = [];
      for (const tcId of tcIds) {
        const tcDoc = await db.collection("trustedContacts").doc(tcId).get();
        if (tcDoc.exists) {
          const fcmToken = tcDoc.data()?.fcmToken;
          if (fcmToken) {
            tokens.push(fcmToken);
          }
        }
      }

      if (tokens.length === 0) {
        console.log(`No valid FCM tokens found for trusted contacts of user ${userId}`);
        return;
      }

      // 3. Dispatch multicast push notification
      const message: admin.messaging.MulticastMessage = {
        tokens: tokens,
        notification: {
          title: `🚨 EMERGENCY: ${userName} Needs Help!`,
          body: `Emergency SOS alert triggered at ${new Date(eventData.triggeredAt).toLocaleTimeString()}. Tap to track location.`,
        },
        data: {
          type: "SOS_ALERT",
          eventId: eventId,
          userId: userId,
          userName: userName,
          latitude: lat,
          longitude: lng,
        },
        android: {
          priority: "high",
          notification: {
            channelId: "sos_alerts",
            priority: "max",
            sound: "default",
            defaultVibrateTimings: true,
          },
        },
      };

      const response = await admin.messaging().sendEachForMulticast(message);
      console.log(`Successfully sent ${response.successCount} SOS push notifications for event ${eventId}`);

      // Update event document with notification delivery count
      await snapshot.ref.update({
        notificationsSent: response.successCount,
      });
    } catch (error) {
      console.error(`Error dispatching SOS notifications for event ${eventId}:`, error);
    }
  });

/**
 * Triggered when an SOS event status changes to RESOLVED or FALSE_ALARM.
 * Sends an All-Clear push notification to all approved Trusted Contacts.
 */
export const onSOSEventUpdated = functions.firestore
  .document("sosEvents/{eventId}")
  .onUpdate(async (change, context) => {
    const beforeData = change.before.data();
    const afterData = change.after.data();

    if (!beforeData || !afterData) return;

    // Only notify if status transitioned from ACTIVE to RESOLVED/FALSE_ALARM
    if (beforeData.status === "ACTIVE" && (afterData.status === "RESOLVED" || afterData.status === "FALSE_ALARM")) {
      const eventId = context.params.eventId;
      const userId = afterData.userId;
      const userName = afterData.userName || "Connected User";
      const isFalseAlarm = afterData.status === "FALSE_ALARM";

      const db = admin.firestore();

      try {
        const relationshipsSnapshot = await db
          .collection("relationships")
          .where("userId", "==", userId)
          .where("permissionState", "==", "APPROVED")
          .get();

        if (relationshipsSnapshot.empty) return;

        const tcIds = relationshipsSnapshot.docs.map((doc) => doc.data().trustedContactId);
        const tokens: string[] = [];

        for (const tcId of tcIds) {
          const tcDoc = await db.collection("trustedContacts").doc(tcId).get();
          if (tcDoc.exists) {
            const token = tcDoc.data()?.fcmToken;
            if (token) tokens.push(token);
          }
        }

        if (tokens.length === 0) return;

        const message: admin.messaging.MulticastMessage = {
          tokens: tokens,
          notification: {
            title: isFalseAlarm ? `ℹ️ False Alarm: ${userName}` : `✅ ALL CLEAR: ${userName} is Safe`,
            body: isFalseAlarm
              ? `${userName} marked the SOS alert as a false alarm.`
              : `${userName} has resolved the emergency alert and is safe now.`,
          },
          data: {
            type: "SOS_RESOLVED",
            eventId: eventId,
            userId: userId,
            userName: userName,
            status: afterData.status,
          },
          android: {
            priority: "high",
            notification: {
              channelId: "sos_alerts",
              sound: "default",
            },
          },
        };

        await admin.messaging().sendEachForMulticast(message);
        console.log(`Sent resolution notifications for SOS event ${eventId}`);
      } catch (error) {
        console.error(`Error sending resolution notification for event ${eventId}:`, error);
      }
    }
  });
