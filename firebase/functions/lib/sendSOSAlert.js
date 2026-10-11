"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.onSOSEventCreated = void 0;
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const twoFactorHelper_1 = require("./twoFactorHelper");
if (!admin.apps.length) {
    admin.initializeApp();
}
const db = admin.firestore();
/**
 * Firestore Trigger: Dispatched automatically when an emergency SOS event document is created.
 * Sends immediate 2Factor SMS alerts to all designated emergency contacts with GPS location coordinates.
 */
exports.onSOSEventCreated = functions.firestore
    .document("sosEvents/{eventId}")
    .onCreate(async (snap, context) => {
    var _a, _b, _c, _d, _e, _f;
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
                smsStatus: "USER_NOT_FOUND",
            });
            return;
        }
        const userData = userDoc.data();
        const emergencyContacts = (userData === null || userData === void 0 ? void 0 : userData.emergencyContacts) || [];
        if (!emergencyContacts || emergencyContacts.length === 0) {
            console.warn(`[onSOSEventCreated] No emergency contacts configured for user ${sosEvent.userId}`);
            await snap.ref.update({
                smsSent: 0,
                smsStatus: "NO_CONTACTS",
                smsDeliveredAt: admin.firestore.FieldValue.serverTimestamp(),
            });
            return;
        }
        const userName = (userData === null || userData === void 0 ? void 0 : userData.name) || sosEvent.userName || "A user";
        const latitude = (_c = (_a = sosEvent.latitude) !== null && _a !== void 0 ? _a : (_b = sosEvent.location) === null || _b === void 0 ? void 0 : _b.latitude) !== null && _c !== void 0 ? _c : 0;
        const longitude = (_f = (_d = sosEvent.longitude) !== null && _d !== void 0 ? _d : (_e = sosEvent.location) === null || _e === void 0 ? void 0 : _e.longitude) !== null && _f !== void 0 ? _f : 0;
        const address = sosEvent.address || "";
        // 2. Dispatch 2Factor SMS alerts in parallel
        const smsPromises = emergencyContacts.map(async (contact) => {
            if (!contact.phone) {
                return {
                    contact: contact.name || "Unknown",
                    phone: "",
                    success: false,
                    error: "Empty phone number",
                };
            }
            const result = await (0, twoFactorHelper_1.sendSOSAlert)(contact.phone, userName, latitude, longitude, address);
            return {
                contact: contact.name || "Emergency Contact",
                phone: contact.phone,
                success: result.success,
                messageId: result.messageId,
                error: result.error,
            };
        });
        const smsResults = await Promise.all(smsPromises);
        const successCount = smsResults.filter((r) => r.success).length;
        // 3. Update the SOS Event document with delivery verification
        await snap.ref.update({
            smsSent: successCount,
            smsStatus: successCount > 0 ? "SENT" : "FAILED",
            smsDeliveryDetails: smsResults,
            smsDeliveredAt: admin.firestore.FieldValue.serverTimestamp(),
        });
        console.log(`[onSOSEventCreated] SOS Event ${eventId}: ${successCount}/${smsResults.length} SMS successfully sent via 2Factor.`);
    }
    catch (error) {
        console.error(`[onSOSEventCreated] Error sending SOS alerts for ${eventId}:`, error);
        await snap.ref.update({
            smsStatus: "FAILED",
            smsError: error instanceof Error ? error.message : "Unknown 2Factor SMS error",
        });
    }
});
//# sourceMappingURL=sendSOSAlert.js.map