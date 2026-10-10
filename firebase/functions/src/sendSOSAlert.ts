import * as functions from "firebase-functions";
import * as admin from "firebase-admin";
import { sendSOSAlert } from "./twilioHelper";

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

/**
 * Firestore Trigger: Dispatched automatically when an emergency SOS event document is created.
 * Sends immediate Twilio SMS alerts to all designated emergency contacts with GPS location coordinates.
 */
export const onSOSEventCreated = functions.firestore
  .document("sosEvents/{eventId}")
  .onCreate(async (snap, context) => {
    const sosEvent = snap.data();
    const eventId = context.params.eventId;

    if (!sosEvent || !sosEvent.userId) {
      console.warn(`[onSOSEventCreated] Missing or invalid SOS event payload for ID: ${eventId}`);
      return;
    }

    console.log(`[onSOSEventCreated] Processing SOS event ${eventId} for user ${sosEvent.userId}`);

    try {
      // 1. Fetch User Profile
      const userDoc = await db.collection("users").doc(sosEvent.userId).get();

      if (!userDoc.exists) {
        console.error(`[onSOSEventCreated] User profile not found for user ID ${sosEvent.userId}`);
        await snap.ref.update({
          smsSent: 0,
          smsDeliveryStatus: "USER_NOT_FOUND",
        });
        return;
      }

      const userData = userDoc.data();
      const emergencyContacts: any[] = userData?.emergencyContacts || [];

      if (!emergencyContacts || emergencyContacts.length === 0) {
        console.warn(`[onSOSEventCreated] No emergency contacts configured for user ${sosEvent.userId}`);
        await snap.ref.update({
          smsSent: 0,
          smsDeliveryStatus: "NO_CONTACTS",
          smsDeliveredAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        return;
      }

      const userName = userData?.name || sosEvent.userName || "A user";
      const latitude = sosEvent.latitude ?? sosEvent.location?.latitude ?? 0;
      const longitude = sosEvent.longitude ?? sosEvent.location?.longitude ?? 0;
      const address = sosEvent.address || "";

      // 2. Dispatch Twilio SMS alerts in parallel
      const smsPromises = emergencyContacts.map(async (contact: { name?: string; phone: string }) => {
        if (!contact.phone) {
          return {
            contact: contact.name || "Unknown",
            phone: "",
            success: false,
            error: "Empty phone number",
          };
        }

        const result = await sendSOSAlert(
          contact.phone,
          userName,
          latitude,
          longitude,
          address
        );

        return {
          contact: contact.name || "Emergency Contact",
          phone: contact.phone,
          success: result.success,
          sid: result.sid,
          error: result.error,
        };
      });

      const smsResults = await Promise.all(smsPromises);
      const successCount = smsResults.filter((r) => r.success).length;
      const failureCount = smsResults.length - successCount;

      // 3. Update the SOS Event document with delivery verification
      await snap.ref.update({
        smsSent: successCount,
        smsDeliveryStatus: successCount > 0 ? "COMPLETED" : "FAILED",
        smsDeliveryDetails: smsResults,
        smsDeliveredAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      console.log(
        `[onSOSEventCreated] SOS Event ${eventId} dispatch summary: ${successCount} SMS succeeded, ${failureCount} failed.`
      );
    } catch (error: any) {
      console.error(`[onSOSEventCreated] Unexpected error processing SOS alert for ${eventId}:`, error);
      await snap.ref.update({
        smsDeliveryStatus: "ERROR",
        smsError: error instanceof Error ? error.message : "Unknown Twilio SMS error",
      });
    }
  });
