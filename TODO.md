### To do
## Remote database:
- [ ] User Authentication (LoginActivity)
- [ ] Get User Data from Server (userId, full name, username, password + Contacts + Session History) (LoginActivity)
- [ ] Populate local Contact and Session History (LoginActivity)
- [ ] User Exist Check (SignupActivity)
- [ ] Add User Data to Server (userId, fullName, username, password hash) (SignupActivity)
- [ ] Get userId from server (SignupActivity)
- [ ] Add Contact to Server Database (contactId, userId, name, phone) (AddContactActivity)
- [ ] Update Contact on Server (contactId, currUserId, name, phone) (EditOrDeleteContactActivity)
- [ ] Delete Contact on Server (EditOrDeleteContactActivity)

- [ ] Add walk session to remote database (sectionId, userId, startTime, endTime, startLat, startLng, destinationName, destinationLat, destinationLng, status) (WalkSessionActivity)

## GPS:
- [ ] Make choosing Destination via Map or Search Bar instead of Buttons (DestinationPickerActivity)
- [ ] Implement a live map feature (WalkSessionActivity, walk_session layout)

## SMS:
- [ ] Send SMS message to all trusted contacts (WalkSessionActivity)

## UI:
- [ ] layout: add_contact, contact_list, destination_picker, edit_or_delete, walk_session, session_history
- [ ] delete redundant layout and activity (activity_session.xml, activity_current_session.xml, SessionActivity, CurrentSessionActivity)

## Other:
- [ ] Password Requirement Check (SignupActivity)

### In progress
- [ ] Add view session history functionality (Main Acitivity)
- [ ] Fetch trusted contacts from local database (WalkSessionActivity)
- [ ] Add walk session to local database (userId, startTime, endTime, startLat, startLng, destinationName, destinationLat, destinationLng, status) (WalkSessionActivity)

