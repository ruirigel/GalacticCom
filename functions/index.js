const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// Function to send notification for a new private message
exports.sendPrivateMessageNotification = functions.region("us-central1").database.ref("/conversations/{conversationId}/messages/{messageId}")
    .onCreate(async (snapshot, context) => {
      const message = snapshot.val();
      const conversationId = context.params.conversationId;

      // Extract recipient ID from conversation ID (assuming format 'uid1_uid2')
      const ids = conversationId.split("_");
      const recipientId = ids[0] === message.senderId ? ids[1] : ids[0];

      // Get sender's nickname and recipient's FCM token
      const senderNicknamePromise = admin.database().ref(`/users/${message.senderId}/nickname`).once("value");
      const recipientTokenPromise = admin.database().ref(`/users/${recipientId}/fcmToken`).once("value");
      const recipientBlockedPromise = admin.database().ref(`/users/${recipientId}/blockedUsers/${message.senderId}`).once("value");

      const [senderNicknameSnapshot, recipientTokenSnapshot, recipientBlockedSnapshot] = await Promise.all([senderNicknamePromise, recipientTokenPromise, recipientBlockedPromise]);

      const senderNickname = senderNicknameSnapshot.val() || "Someone";
      const recipientToken = recipientTokenSnapshot.val();

      // Do not send if the recipient has blocked the sender
      if (recipientBlockedSnapshot.exists()) {
        console.log(`Notification blocked from ${message.senderId} to ${recipientId}.`);
        return null;
      }

      if (recipientToken) {
        // Use Data Message to allow client-side processing (decryption)
        const messagePayload = {
          token: recipientToken,
          data: {
            type: "private_message",
            senderId: message.senderId,
            recipientId: recipientId,
            conversationId: conversationId,
            title: `New message from ${senderNickname}`,
            body: message.messageText || "Image/GIF/Voice message received"
          },
          android: {
            priority: "high"
          }
        };
        
        console.log("Sending private message notification to:", recipientToken);
        try {
            return await admin.messaging().send(messagePayload);
        } catch (error) {
            console.error("Error sending private message:", error);
            return null;
        }
      }
      return null;
    });

// Function to send notification for a new public message
exports.sendPublicMessageNotification = functions.region("us-central1").database.ref("/public_broadcasts/{galaxyName}/{messageId}")
    .onCreate(async (snapshot, context) => {
      const message = snapshot.val();
      const galaxyName = context.params.galaxyName.replace(/_/g, " ");
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
        if (childSnapshot.key !== message.senderId) {
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
            galaxy: galaxyName,
            senderId: message.senderId,
            title: `Public Transmission from ${senderNickname}`,
            body: message.message || `A new signal has been detected in the ${galaxyName} galaxy.`
          },
          android: {
            priority: "high"
          }
        };
        
        console.log(`Sending public message notification to ${tokens.length} users.`);
        try {
            return await admin.messaging().sendEachForMulticast(messagePayload);
        } catch (error) {
            console.error("Error sending public message:", error);
            return null;
        }
      }
      return null;
    });

// Corrected function to notify users of pirate arrival
exports.notifyUsersOfPirateArrival = functions.region("us-central1").database.ref("/merchants/{merchantId}")
    .onUpdate(async (change, context) => {
      const beforeData = change.before.val();
      const afterData = change.after.val();

      // A notification should be sent only when a merchant becomes visible
      const justBecameVisible = !beforeData.visible && afterData.visible;

      if (!justBecameVisible) {
        console.log("No notification sent: merchant did not just become visible.");
        return null;
      }
      
      const galaxyName = afterData.current_galaxy;
      const pirateName = afterData.merchant_name || "a Pirate Merchant";

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
            pirateName: pirateName,
            galaxy: galaxyName,
            title: "Pirate Sighting!",
            body: `${pirateName} has been spotted in the ${galaxyName} galaxy!`
          },
          android: {
            priority: "high"
          }
        };

        console.log(`Sending pirate arrival notification to ${tokens.length} users in ${galaxyName}.`);
        try {
            return await admin.messaging().sendEachForMulticast(messagePayload);
        } catch (error) {
            console.error("Error sending pirate arrival:", error);
            return null;
        }
      }

      return null;
    });

// SCHEDULED FUNCTION: Automatically move the pirate when time expires
// Runs every 15 minutes to check if the pirate needs to move
exports.scheduledPirateMove = functions.region("us-central1").pubsub.schedule("every 15 minutes").onRun(async (context) => {
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
    return null;
});