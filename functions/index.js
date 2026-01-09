const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// Configurações de runtime para evitar problemas de cold start/timeout
const runtimeOpts = {
  timeoutSeconds: 60,
  memory: "256MB",
  maxInstances: 10 // Limite razoável para evitar custos excessivos em loop
};

// Function to send notification for a new private message
exports.sendPrivateMessageNotification = functions.region("us-central1")
    .runWith(runtimeOpts)
    .database.ref("/conversations/{conversationId}/messages/{messageId}")
    .onCreate(async (snapshot, context) => {
      const message = snapshot.val();
      const conversationId = context.params.conversationId;

      if (!message || !message.senderId) return null;

      // Extract recipient ID from conversation ID safely
      // Ensure we compare strings to strings
      const senderIdStr = String(message.senderId);
      const ids = conversationId.split("_");
      
      // Validação básica do ID da conversa
      if (ids.length < 2) {
          console.error(`Invalid conversation ID format: ${conversationId}`);
          return null;
      }

      const recipientId = ids[0] === senderIdStr ? ids[1] : ids[0];
      
      if (!recipientId) {
          console.error("Could not determine recipient ID.");
          return null;
      }

      // Get sender's nickname and recipient's FCM token
      // Use String() on paths just in case
      const senderNicknamePromise = admin.database().ref(`/users/${senderIdStr}/nickname`).once("value");
      const recipientTokenPromise = admin.database().ref(`/users/${recipientId}/fcmToken`).once("value");
      const recipientBlockedPromise = admin.database().ref(`/users/${recipientId}/blockedUsers/${senderIdStr}`).once("value");

      try {
          const [senderNicknameSnapshot, recipientTokenSnapshot, recipientBlockedSnapshot] = await Promise.all([senderNicknamePromise, recipientTokenPromise, recipientBlockedPromise]);

          const senderNickname = senderNicknameSnapshot.val() || "Someone";
          const recipientToken = recipientTokenSnapshot.val();

          // Do not send if the recipient has blocked the sender
          if (recipientBlockedSnapshot.exists()) {
            console.log(`Notification blocked from ${senderIdStr} to ${recipientId}.`);
            return null;
          }

          if (recipientToken) {
            // Use Data Message to allow client-side processing (decryption)
            // CRITICAL FOR V1: All values in 'data' MUST be strings
            const messagePayload = {
              token: recipientToken,
              data: {
                type: "private_message",
                senderId: String(senderIdStr),
                recipientId: String(recipientId),
                conversationId: String(conversationId),
                title: String(`New message from ${senderNickname}`),
                body: String(message.messageText || "Image/GIF/Voice message received")
              },
              android: {
                priority: "high"
              }
            };
            
            console.log("Sending private message notification to:", recipientToken);
            return await admin.messaging().send(messagePayload);
          }
      } catch (error) {
          console.error("Error in sendPrivateMessageNotification:", error);
      }
      return null;
    });

// Function to send notification for a new public message
exports.sendPublicMessageNotification = functions.region("us-central1")
    .runWith(runtimeOpts)
    .database.ref("/public_broadcasts/{galaxyName}/{messageId}")
    .onCreate(async (snapshot, context) => {
      const message = snapshot.val();
      const galaxyName = context.params.galaxyName.replace(/_/g, " ");
      
      try {
          const senderNickname = (await admin.database().ref(`/users/${message.senderId}/nickname`).once("value")).val() || "A captain";

          // Get all users in that galaxy
          const usersRef = admin.database().ref("/users");
          const snapshotUsers = await usersRef.orderByChild("galaxy").equalTo(galaxyName).once("value");

          if (!snapshotUsers.exists()) {
            console.log(`No users found in galaxy: ${galaxyName}`);
            return null;
          }

          const tokens = [];
          snapshotUsers.forEach((childSnapshot) => {
            // Do not send notification to the message sender
            if (childSnapshot.key !== String(message.senderId)) {
              const userToken = childSnapshot.child("fcmToken").val();
              // Check if this user has blocked the sender
              const isBlocked = childSnapshot.child(`blockedUsers/${message.senderId}`).exists();
              if (userToken && !isBlocked) {
                tokens.push(userToken);
              }
            }
          });

          if (tokens.length > 0) {
            // Use Data Message
            const messagePayload = {
              tokens: tokens,
              data: {
                type: "public_message",
                galaxy: String(galaxyName),
                senderId: String(message.senderId),
                title: String(`Public Transmission from ${senderNickname}`),
                body: String(message.message || `A new signal has been detected in the ${galaxyName} galaxy.`)
              },
              android: {
                priority: "high"
              }
            };
            
            console.log(`Sending public message notification to ${tokens.length} users.`);
            const response = await admin.messaging().sendEachForMulticast(messagePayload);
            if (response.failureCount > 0) {
                console.log(`Failed to send ${response.failureCount} public messages.`);
            }
            return response;
          }
      } catch (error) {
          console.error("Error in sendPublicMessageNotification:", error);
      }
      return null;
    });

// Corrected function to notify users of pirate arrival
exports.notifyUsersOfPirateArrival = functions.region("us-central1")
    .runWith(runtimeOpts)
    .database.ref("/merchants/{merchantId}")
    .onUpdate(async (change, context) => {
      const beforeData = change.before.val();
      const afterData = change.after.val();

      // A notification should be sent only when a merchant becomes visible
      const justBecameVisible = !beforeData.visible && afterData.visible;

      if (!justBecameVisible) {
        return null;
      }
      
      const galaxyName = afterData.current_galaxy;
      const pirateName = afterData.merchant_name || "a Pirate Merchant";

      try {
          // Get all users in that galaxy
          const usersRef = admin.database().ref("/users");
          const usersSnapshot = await usersRef.orderByChild("galaxy").equalTo(galaxyName).once("value");

          if (!usersSnapshot.exists()) {
            console.log(`No users found in galaxy: ${galaxyName} for pirate arrival.`);
            return null;
          }

          const tokens = [];
          usersSnapshot.forEach((childSnapshot) => {
            const userToken = childSnapshot.child("fcmToken").val();
            if (userToken) {
              tokens.push(userToken);
            }
          });

          if (tokens.length > 0) {
            // Use Data Message
            const messagePayload = {
              tokens: tokens,
              data: {
                type: "pirate_arrival",
                pirateName: String(pirateName),
                galaxy: String(galaxyName),
                title: "Pirate Sighting!",
                body: String(`${pirateName} has been spotted in the ${galaxyName} galaxy!`)
              },
              android: {
                priority: "high"
              }
            };

            console.log(`Sending pirate arrival notification to ${tokens.length} users in ${galaxyName}.`);
            const response = await admin.messaging().sendEachForMulticast(messagePayload);
            if (response.failureCount > 0) {
                console.log(`Failed to send ${response.failureCount} pirate arrival messages.`);
            }
            return response;
          }
      } catch (error) {
          console.error("Error in notifyUsersOfPirateArrival:", error);
      }
      return null;
    });

// SCHEDULED FUNCTION: Automatically move the pirate when time expires
// Runs every 15 minutes to check if the pirate needs to move
exports.scheduledPirateMove = functions.region("us-central1")
    .runWith({ timeoutSeconds: 540, memory: "256MB" }) // More time for scheduled tasks if needed
    .pubsub.schedule("every 15 minutes").onRun(async (context) => {
    
    try {
        const merchantRef = admin.database().ref("/merchants/captain_silas");
        const snapshot = await merchantRef.once("value");
        const merchant = snapshot.val();

        if (!merchant) {
            console.log("Captain Silas not found in database.");
            return null;
        }

        const currentTime = Date.now();
        
        // Check if the current time has passed the visible_until timestamp
        if (currentTime > merchant.visible_until) {
            console.log("Captain Silas's time has expired. Initiating jump...");

            // 1. Get a list of inhabited galaxies
            const usersSnapshot = await admin.database().ref("/users").once("value");
            const inhabitedGalaxies = new Set();
            usersSnapshot.forEach((userDoc) => {
                const galaxy = userDoc.child("galaxy").val();
                if (galaxy) inhabitedGalaxies.add(galaxy);
            });

            let galaxyList = Array.from(inhabitedGalaxies);
            
            // FILTER: Remove current galaxy from list if there are other options
            if (merchant.current_galaxy && galaxyList.length > 1) {
                 galaxyList = galaxyList.filter(g => g !== merchant.current_galaxy);
            }

            if (galaxyList.length === 0) {
                console.log("No inhabited galaxies found to move to.");
                return null;
            }

            // 2. Choose a random galaxy
            const newGalaxy = galaxyList[Math.floor(Math.random() * galaxyList.length)];

            // 3. Get the duration setting (default 8 hours)
            const settingsSnapshot = await admin.database().ref("/settings/merchantAppearanceDurationHours").once("value");
            const durationHours = settingsSnapshot.val() || 8;
            const newVisibleUntil = currentTime + (durationHours * 60 * 60 * 1000);

            // 4. Move the pirate!
            // We set 'visible' to false first, then true, to ensure the 'notifyUsersOfPirateArrival' trigger fires properly.
            await merchantRef.update({ visible: false });
            
            await merchantRef.update({
                current_galaxy: newGalaxy,
                visible_until: newVisibleUntil,
                visible: true
            });

            console.log(`Captain Silas moved to ${newGalaxy}. Next jump at: ${new Date(newVisibleUntil).toISOString()}`);
        } else {
            console.log("Captain Silas is still busy trading. No move needed.");
        }
    } catch (error) {
        console.error("Error in scheduledPirateMove:", error);
    }
    return null;
});