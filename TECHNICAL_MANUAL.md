# GalacticCom Technical Manual

This document serves as a technical guide to the architecture, logic, and data flows of the GalacticCom application.

### Table of Contents

1.  [Game Architecture & Global Rules](#game-architecture--global-rules)
2.  [Resource Mining (`SystemFragment`)](#resource-mining-systemfragment)
3.  [The Pirate Merchant (Captain harlock)](#the-pirate-merchant-captain-silas)
4.  [Authentication (`LoginFragment` & `RegisterFragment`)](#authentication-loginfragment--registerfragment)
5.  [Main Screen (`HomeFragment`)](#main-screen-homefragment)
6.  [Galaxy Directory (`GalaxyDirectoryFragment`)](#galaxy-directory-galaxydirectoryfragment)
7.  [Compose Transmission (`ComposeDeepSpaceFragment`)](#compose-transmission-composedeepspacefragment)
8.  [Inbox (`InboxFragment`)](#inbox-inboxfragment)
9.  [Conversation Screen (`ConversationFragment`)](#conversation-screen-conversationfragment)
10. [Settings & Profile (`SettingsFragment` & `UserProfileDialogFragment`)](#settings--profile-settingsfragment--userprofiledialogfragment)
11. [Push Notification Service (`MyFirebaseMessagingService` & Cloud Functions)](#push-notification-service-myfirebasemessagingservice--cloud-functions)
12. [Badge System (`BadgeManager` & `BadgeProgressManager`)](#badge-system-badgemanager--badgeprogressmanager)

---

### **Game Architecture & Global Rules**

A centralized system for game settings and player action tracking has been implemented to make the application's universe feel consistent and governed by a set of rules.

#### **1. Global Settings (`/settings`)**

The `/settings` path in the Firebase Realtime Database is the single source of truth for all game-wide rules.

*   **`SettingsManager.kt`**: A singleton object responsible for loading all values from `/settings` on app startup and providing cached accessors for the rest of the app.
*   **Database Structure:** Contains key-value pairs for game constants like `publicMessageDailyLimit`, `planetaryTravelTimeMinutes`, `weeklyIntergalacticTravelLimit`, etc.

#### **2. User Action Logs (`/users/{uid}/actionLogs`)**

To enforce the limits, each user's profile contains an `actionLogs` object that tracks the timestamp and count of limited actions.

*   **Logic:** Before performing a limited action, the app reads the relevant log, resets the counter if a new day/week/month has begun, compares the count against the limit from `SettingsManager`, and then updates the log if the action is successful.

---

### **Resource Mining (`SystemFragment`)**

This fragment manages the core gameplay loop of passive resource generation. It displays the user's current planetary system and handles the logic for mining, resource collection, and system stress.

*   **Database Structure (`/users/{uid}/inventory_data`):**
    *   **`lastCollectionTimestamp`**: The timestamp of the last time the user collected resources. Used to calculate accumulated resources.
    *   **`miningStartTime`**: The timestamp when the current continuous mining session began. Used to calculate system stress.
    *   **`planetTotalReserves`**: The total amount of a resource available on the current planet. Regenerated upon planetary travel. Initialized to -1 for new planets.
    *   **`resources`**: A map holding the quantities of collected resources (e.g., `iron_ore`, `gold_dust`).

*   **Client-Side Logic:**
    *   **Procedural Generation:** The type of resource on a planet and its initial reserves are generated procedurally using the galaxy and planet names as a seed.
    *   **Passive Accumulation (`getAccumulatedAmount`):** The fragment calculates the amount of accumulated (uncollected) resources based on the time elapsed since `lastCollectionTimestamp`. The rate is approximately 1 unit per minute.
    *   **System Stress & Overheat:**
        *   The system's "stress" is calculated based on the time elapsed since `miningStartTime`.
        *   After a continuous 4-hour period (`MAX_MINING_TIME_MS`), the system "overheats," and resource accumulation stops until the system is cooled down.
    *   **Resource Collection (`collectResources`):** When the user collects, the accumulated amount is added to their `resources` map, `planetTotalReserves` is reduced, and `lastCollectionTimestamp` is updated to the current time.
    *   **Ad Monetization Hooks:**
        *   **Cool Down (`showCoolDownAd`):** If the system is overheated, the user can watch a rewarded ad. On completion, the `collectResources` function is called with `resetStress = true`, which updates the `miningStartTime` to the current time, resetting the stress meter.
        *   **Recharge (`showRechargeAd`):** If a planet's reserves are depleted, an ad can be watched to add a fixed amount (e.g., 20,000) to `planetTotalReserves`.
    *   **UI (`updateAccumulatedResourcesUI`):** A central method that runs on a 1-second timer to update the UI for accumulated resources, reserves, stress percentage, and the state of the collection/ad buttons.

---

### **The Pirate Merchant (Captain Harlock)**

This feature introduces a dynamic NPC merchant into the game who buys and sells items. The currency used for transactions is **Credits**.

*   **Database Structure:**
    *   **Merchant Data:** The `/merchants/captain_silas` path holds the merchant's profile, including `merchant_name`, `ship_name`, `avatar_seed`, current `location`, `visible_until` timestamp, and `inventory`.
    *   **User Inventory:** The user's balance and resources are stored under `/users/{uid}/inventory_data`. This object contains `credits` (Long) and a map of `resources` (e.g., `iron_ore`, `gold_dust`).
*   **Client-Side Logic (`HomeFragment`):**
    *   **Visibility Check:** The app continuously monitors the `merchants/captain_silas` node. If `visible_until` is in the future and the merchant is in the user's current galaxy, a floating action button (FAB) and a notification balloon appear.
    *   **Auto-Relocation:** If the `visible_until` timestamp has expired, the first user who opens the app triggers a `relocateMerchant` function. This function picks a new random galaxy from the list of currently inhabited galaxies and sets a new `visible_until` time.
*   **Merchant Interaction (`PirateStoreFragment`):**
    *   This fragment uses a `ViewPager2` with a `TabLayout` to provide two main sections: "BUY ITEMS" (`PirateBuyFragment`) and "SELL ORE" (`PirateSellFragment`).
    *   **Buy Flow (`PirateBuyFragment`):** The store dialog verifies if the user has enough `credits` in their `inventory_data`. Upon purchase, credits are deducted, and the item's specific benefit is added to the user's profile (e.g., incrementing `inventory/hyperwave_booster`).
    *   **Sell Flow (`PirateSellFragment`):**
        *   The fragment reads the user's `inventory_data/resources`.
        *   It displays a list of sellable resources, including `iron_ore`, `gold_dust`, `plasma_crystal`, and `dark_matter`.
        *   Selling is handled by a Firebase `Transaction` that atomically removes the resource from the user's inventory and adds the corresponding value in `credits`.

---

### **Authentication (`LoginFragment` & `RegisterFragment`)**

*   **`RegisterFragment`**: Handles new account creation.
    *   **Nickname Validation:** Enforces that nicknames must be at least 3 characters long and contain only letters, digits, underscores (_), and hyphens (-).
    *   **Cosmic Generation:** Procedurally generates galaxies, stars, and planets for the user to choose their origin.
    *   **Live Avatar Preview:** The `AlienAvatarGenerator` creates a preview of the avatar in real-time as the user types their nickname.
    *   **Two-Step Creation:** Creates the user in Firebase Authentication first, and only then saves the profile data to the Realtime Database. A rollback mechanism (`user.delete()`) is in place if the database write fails.
    *   **Action Logs Creation:** The user's profile is created with an `actionLogs` object, initialized with default values.
*   **`LoginFragment`**:
    *   **Email Verification:** Strictly enforces that `user.isEmailVerified` is `true` before allowing login.
    *   **FCM Token Update:** Upon successful login, the latest FCM token is fetched and stored in the user's profile to ensure notifications work correctly.

---

### **Main Screen (`HomeFragment`)**

*   **Primary Responsibility:** Displays public transmissions (`PublicMessage`) from other users within the same galaxy. It features a visual "balloon view" and a traditional "list view".
*   **Data Flow & Consistency:**
    *   **User State Check:** Listens to the user's profile. If `travelCompletionTimestamp` is in the future, it redirects to the travel screen.
    *   **Message Loading:** Performs an initial load of existing messages and then attaches a `ChildEventListener` to receive new messages in real-time.
    *   **Stale Conversation Cleanup:** When the screen loads, it verifies that every conversation ID in the user's profile exists in the root `/conversations` node. Any reference to a deleted conversation is automatically removed from the user's profile to prevent errors and ensure data consistency.
*   **Filtering Logic:** A message is only displayed if it does not belong to the current user, is not part of an existing private conversation, is not in the user's `hiddenPublicMessages` list, and is not from a blocked user.
*   **Interactive UI (Balloons):**
    *   Manages a pool of 14 `BalloonTextViews`.
    *   Displays messages with a future `arrivalTime` with a grayed-out icon, which turns to cyan when the message "arrives".
    *   Balloons expire 12 hours after their arrival time, freeing up space for new messages.
*   **View Toggle Mechanism:**
    *   The previous `FloatingActionButton` for toggling views has been replaced with a **long-press gesture** on the bottom navigation menu item for "Broadcast".
    *   The `MainActivity` captures this gesture and invokes the `toggleViewMode()` method on the `HomeFragment`.
*   **New Additions:**
    *   **Merchant Logic:** Contains the logic for listening to the merchant's status, triggering his relocation, and displaying the UI elements (FAB and balloon) when he is present.

---

### **Galaxy Directory (`GalaxyDirectoryFragment`)**

*   **Primary Responsibility:** Acts as the "cosmos map," allowing users to browse galaxies and initiate intergalactic travel.
*   **Data Flow:** Aggregates data by counting messages in `public_broadcasts` and inhabitants in `users` for each galaxy. Procedural data (morphology, age) is generated by `CosmicNameGenerator`.
*   **View Toggle Mechanism:**
    *   The previous `FloatingActionButton` for toggling between "List" and "Map" views has been replaced with a **long-press gesture** on the bottom navigation menu item for "Cosmos".
    *   The `MainActivity` captures this gesture and invokes the `toggleViewMode()` method on the `GalaxyDirectoryFragment`.
*   **Travel Logic:**
    *   **New:** The previous travel limit logic using `SharedPreferences` has been **removed**.
    *   **New:** Before showing the travel confirmation dialog, the `checkIntergalacticTravelLimitAndProceed` function is now called to check the limit against `SettingsManager` and the user's `actionLogs`.
    *   **New:** The travel time is now fetched dynamically from `SettingsManager`.
    *   Upon successful travel, the user's location is updated, a `travelCompletionTimestamp` is set, and the `actionLogs` are updated.
    *   **Data Persistence:** When a user leaves a galaxy, the messages in that galaxy (`public_broadcasts/GalaxyName`) are NOT deleted. They remain there for other inhabitants. If the galaxy becomes empty (0 inhabitants), it disappears from the directory list, but the data persists in the database.

---

### **Compose Transmission (`ComposeDeepSpaceFragment`)**

*   **Primary Responsibility:** Allows the user to write and send a public message to deep space.
*   **Sending Logic:**
    *   **Random Destination:** The system selects a random user from the database and sends the message to their galaxy.
    *   **New:** The previous daily limit logic using `SharedPreferences` has been **removed**.
    *   **New:** The `checkPublicMessageLimitAndSend` function is now called to verify the daily limit against `SettingsManager` and the user's `actionLogs`.
    *   **Item Integration:** Before sending, checks if the user has a `Hyperwave Booster` (`item_001`). If yes, it consumes one item and sets the message delay to 0.
    *   **Catastrophe Engine:** A system that can randomly corrupt the message content or add a delay to its arrival time.
    *   **Arrival Time Logic:** The delivery time is calculated based on a 24-hour base time, minus a discount based on the user's experience points (XP), with a minimum of 1 hour.
    *   **Post-Send Effects:** Sending a message grants the user XP and saves the original, uncorrupted message to their local `sent_messages` history.

---

### **Inbox (`InboxFragment`)**

*   **Primary Responsibility:** Displays a list of all private conversations.
*   **Conversation Initiation:** New conversations are started via an "invitation" system. When a user replies to a public message, an invitation is created under `invitations/{recipientId}`. The `InboxFragment` processes these invitations and moves them to the user's main conversation list.
*   **Data Loading & Synchronization:** The fragment loads conversation details in parallel using a `CountDownLatch`. If a conversation's details cannot be retrieved (e.g., because it was deleted by the other user), the reference is automatically removed from the current user's profile to keep the inbox synchronized.
*   **Conversation Deletion:** Deleting a conversation is a destructive multi-step operation that removes files from Firebase Storage, then deletes all related database entries for both users.

---

### **Conversation Screen (`ConversationFragment`)**

*   **Primary Responsibility:** The real-time chat screen for a private conversation.
*   **Message Sending (`sendOrUpdateReply`):**
    *   **New:** The send button now calls `checkPrivateMessageLimitAndSend`, which verifies the daily limit against `SettingsManager` and the user's `actionLogs` before proceeding.
    *   **Item Integration:** If the daily limit is reached, it checks for `Clandestine Cargo` (`item_002`) in the user's inventory. If available, consumes one unit and allows sending.
    *   **Encryption:** If the conversation is marked as private, messages are encrypted using `CryptoManager` before being sent. When a message is deleted, the "Message deleted" marker is also encrypted.
    *   **Rich Content:** Supports sending images, GIFs, and voice messages. 
    *   **Voice Message Limit:** Voice recordings are limited to a maximum of 1 minute. If the limit is reached, the recording stops automatically and the message is sent.
*   **Message Loading:** The initial message load and new message listening have been unified into a single `addChildEventListener` to improve performance and prevent UI flicker.
*   **Advanced Features (`ActionMode`):** Supports replying, editing, and soft-deleting messages with an optimistic UI update for a smoother user experience.
*   **Pagination:** Initially loads the last 20 messages and loads older pages as the user scrolls to the top.

---

### **Settings & Profile (`SettingsFragment` & `UserProfileDialogFragment`)**

*   **`SettingsFragment`**: The user's control panel for profile and app settings.
    *   **Profile Management:** Allows editing of nickname and bio.
    *   **Planetary Travel (`Find New System`):** The UI for this action is located in `SystemFragment`. The action is limited by `dailyPlanetaryTravelLimit` from `SettingsManager`, verified against the user's `actionLogs`. Upon travel, `planetTotalReserves` is reset to allow for new resource generation.
    *   **Account Deletion:** A secure, multi-step process that requires re-authentication, deletes all associated data from Storage and Realtime Database, and finally deletes the user from Firebase Authentication.
    *   **Blocked Users Management:** A dialog (`dialog_blocked_users.xml`) that lists blocked users (`item_blocked_user.xml`) and allows unblocking them.
*   **`UserProfileDialogFragment`**:
    *   **New:** The "Regenerate Avatar" button (visible on one's own profile) now checks the `monthlyAvatarChangeLimit` against `SettingsManager` and the user's `actionLogs`. If the limit is reached, it checks for `Avatar Seed` (`item_004`) in inventory to allow extra changes.
    *   **Emblem Display:** Checks for `emblems/lone_traveler` in the user's profile and displays a gold star icon next to the name if present.

---

### **Push Notification Service (`MyFirebaseMessagingService` & Cloud Functions)**

The push notification system is a collaboration between the Android client and Firebase Cloud Functions.

#### **Client-Side (`MyFirebaseMessagingService.kt`)**

*   **`onNewToken`**: Manages the FCM token, saving it to the user's profile to identify their device for notifications.
*   **`onMessageReceived`**: This is the central hub for incoming data messages.
    *   **Filtering:** Ignores notifications from the user themselves or from any user on their blocklist.
    *   **Foreground vs. Background:** If the app is in the foreground, it doesn't show a notification but instead sends a `LocalBroadcast` to the `MainActivity` to display a badge on the bottom navigation bar. If the app is in the background, it shows a standard system notification.
    *   **Message Routing:** Uses a `when` statement on the `type` field in the message data to handle different notification types (`private_message`, `public_message`, etc.).

#### **Server-Side (Cloud Functions in `index.js`)**

*   **`sendPrivateMessageNotification`**: Triggered on new message creation in `/conversations`. Sends a notification to the recipient of a private message.
*   **`sendPublicMessageNotification`**: Triggered on new message creation in `/public_broadcasts`. Sends a notification to all users in the target galaxy.
*   **NEW: `notifyUsersOfPirateArrival`**:
    *   **Trigger:** This function is triggered by the `.onCreate()` event on the Realtime Database path `/merchants/{merchantId}`. It activates the moment the game's logic "spawns" a new pirate merchant.
    *   **Logic:**
        1.  Reads the new merchant's data to get their name (`merchant_name`) and location (`current_galaxy`).
        2.  Queries the `/users` node to find all users whose `galaxy` property matches the merchant's location.
        3.  Collects the `fcmToken` from each of these users.
    *   **Payload:** Sends a data-only FCM message to the collected tokens with the following structure:
        ```json
        {
          "data": {
            "type": "pirate_arrival",
            "pirateName": "Captain Harlock",
            "galaxy": "Andromeda"        
          }
        }
        ```
    *   **Client-Side Handling:** The `MyFirebaseMessagingService` receives this payload, identifies the `pirate_arrival` type, and constructs a randomized, captivating notification message for the user, ensuring they are alerted to the event even if the app is closed.

---

### **Badge System (`BadgeManager` & `BadgeProgressManager`)**

A long-term retention system tracking user achievements over a 10-year period.

*   **Architecture:**
    *   **`ActionLogs` (Model):** Tracks cumulative statistics like `totalYearsActive`, `miningTotalAccumulated`, `visitedGalaxies`, etc.
    *   **`Badge` (Model):** Represents the visual state of a badge (Bronze, Silver, Gold, etc.) and progress percentage.
    *   **`BadgeManager` (Logic):** Calculates badge unlocks dynamically based on `ActionLogs`.
    *   **`BadgeProgressManager` (Helper):** Provides static methods to easily increment stats in Firebase transactions.

*   **Implementation:**
    *   The `BadgeProgressManager` is called in key locations (mining, travel, messaging) to update the database.
    *   The `UserProfileDialogFragment` fetches the `actionLogs`, uses `BadgeManager` to calculate the current state, and displays the badges in a `RecyclerView` using `BadgeAdapter`.
