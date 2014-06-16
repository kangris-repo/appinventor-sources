package com.google.appinventor.client;
import static com.google.appinventor.client.Ode.MESSAGES;

import com.google.appinventor.client.output.OdeLog;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TranslationComponentEvents {

  public static String languageSetting;
  public static Map<String, String> myMap = map();

  public static enum Language {
    zh_CN, en_US
  }


  public static String getName(String key) {
    if (!myMap.containsKey(key)) {
      OdeLog.log("Map does not contain key " + key);
      return key;
    }
    return myMap.get(key);
  }
  

  /**
   * Get a translation map.
   * 
   * The output map has the following format: 
   *    Map = [{eventName1: EventPair1}, ...]
   * 
   * @return map
   */
  public static HashMap<String, String> map() { 
    HashMap<String, String> map = new HashMap<String, String>();
    
    // Events
    map.put("AccelerationChanged", MESSAGES.AccelerationChangedEvents());
	map.put("AfterActivity", MESSAGES.AfterActivityEvents());
	map.put("CollidedWith", MESSAGES.CollidedWithEvents());
	map.put("Dragged", MESSAGES.DraggedEvents());
	map.put("EdgeReached", MESSAGES.EdgeReachedEvents());
	map.put("Flung", MESSAGES.FlungEvents());
	map.put("NoLongerCollidingWith", MESSAGES.NoLongerCollidingWithEvents());
	map.put("TouchDown", MESSAGES.TouchDownEvents());
	map.put("TouchUp", MESSAGES.TouchUpEvents());
	map.put("Touched", MESSAGES.TouchedEvents());
	map.put("AfterScan", MESSAGES.AfterScanEvents());
	map.put("ConnectionAccepted", MESSAGES.ConnectionAcceptedEvents());
	map.put("Click", MESSAGES.ClickEvents());
	map.put("GotFocus", MESSAGES.GotFocusEvents());
	map.put("LongClick", MESSAGES.LongClickEvents());
	map.put("LostFocus", MESSAGES.LostFocusEvents());
	map.put("AfterRecording", MESSAGES.AfterRecordingEvents());
	map.put("AfterPicture", MESSAGES.AfterPictureEvents());
	map.put("Changed", MESSAGES.ChangedEvents());
	map.put("Timer", MESSAGES.TimerEvents());
	map.put("AfterPicking", MESSAGES.AfterPickingEvents());
	map.put("BeforePicking", MESSAGES.BeforePickingEvents());
	map.put("BackPressed", MESSAGES.BackPressedEvents());
	map.put("ErrorOccurred", MESSAGES.ErrorOccurredEvents());
	map.put("Initialize", MESSAGES.InitializeEvents());
	map.put("OtherScreenClosed", MESSAGES.OtherScreenClosedEvents());
	map.put("ScreenOrientationChanged", MESSAGES.ScreenOrientationChangedEvents());
	map.put("GotResult", MESSAGES.GotResultEvents());
	map.put("FunctionCompleted", MESSAGES.FunctionCompletedEvents());
	map.put("GotMessage", MESSAGES.GotMessageEvents());
	map.put("Info", MESSAGES.InfoEvents());
	map.put("InstanceIdChanged", MESSAGES.InstanceIdChangedEvents());
	map.put("Invited", MESSAGES.InvitedEvents());
	map.put("NewInstanceMade", MESSAGES.NewInstanceMadeEvents());
	map.put("NewLeader", MESSAGES.NewLeaderEvents());
	map.put("PlayerJoined", MESSAGES.PlayerJoinedEvents());
	map.put("PlayerLeft", MESSAGES.PlayerLeftEvents());
	map.put("ServerCommandFailure", MESSAGES.ServerCommandFailureEvents());
	map.put("ServerCommandSuccess", MESSAGES.ServerCommandSuccessEvents());
	map.put("UserEmailAddressSet", MESSAGES.UserEmailAddressSetEvents());
	map.put("WebServiceError", MESSAGES.WebServiceErrorEvents());
	map.put("LocationChanged", MESSAGES.LocationChangedEvents());
	map.put("StatusChanged", MESSAGES.StatusChangedEvents());
	map.put("AfterChoosing", MESSAGES.AfterChoosingEvents());
	map.put("AfterTextInput", MESSAGES.AfterTextInputEvents());
	map.put("AboveRange", MESSAGES.AboveRangeEvents());
	map.put("BelowRange", MESSAGES.BelowRangeEvents());
	map.put("ColorChanged", MESSAGES.ColorChangedEvents());
	map.put("WithinRange", MESSAGES.WithinRangeEvents());
	map.put("Pressed", MESSAGES.PressedEvents());
	map.put("Released", MESSAGES.ReleasedEvents());
	map.put("OrientationChanged", MESSAGES.OrientationChangedEvents());
	map.put("CalibrationFailed", MESSAGES.CalibrationFailedEvents());
	map.put("GPSAvailable", MESSAGES.GPSAvailableEvents());
	map.put("GPSLost", MESSAGES.GPSLostEvents());
	map.put("SimpleStep", MESSAGES.SimpleStepEvents());
	map.put("StartedMoving", MESSAGES.StartedMovingEvents());
	map.put("StoppedMoving", MESSAGES.StoppedMovingEvents());
	map.put("WalkStep", MESSAGES.WalkStepEvents());
	map.put("Completed", MESSAGES.CompletedEvents());
	map.put("AfterSoundRecorded", MESSAGES.AfterSoundRecordedEvents());
	map.put("StartedRecording", MESSAGES.StartedRecordingEvents());
	map.put("StoppedRecording", MESSAGES.StoppedRecordingEvents());
	map.put("AfterGettingText", MESSAGES.AfterGettingTextEvents());
	map.put("BeforeGettingText", MESSAGES.BeforeGettingTextEvents());
	map.put("AfterSpeaking", MESSAGES.AfterSpeakingEvents());
	map.put("BeforeSpeaking", MESSAGES.BeforeSpeakingEvents());
	map.put("MessageReceived", MESSAGES.MessageReceivedEvents());
	map.put("SendMessage", MESSAGES.SendMessageEvents());
	map.put("GotValue", MESSAGES.GotValueEvents());
	map.put("ValueStored", MESSAGES.ValueStoredEvents());
	map.put("DirectMessagesReceived", MESSAGES.DirectMessagesReceivedEvents());
	map.put("FollowersReceived", MESSAGES.FollowersReceivedEvents());
	map.put("FriendTimelineReceived", MESSAGES.FriendTimelineReceivedEvents());
	map.put("IsAuthorized", MESSAGES.IsAuthorizedEvents());
	map.put("MentionsReceived", MESSAGES.MentionsReceivedEvents());
	map.put("SearchSuccessful", MESSAGES.SearchSuccessfulEvents());
	map.put("GotBallot", MESSAGES.GotBallotEvents());
	map.put("GotBallotConfirmation", MESSAGES.GotBallotConfirmationEvents());
	map.put("NoOpenPoll", MESSAGES.NoOpenPollEvents());
	map.put("GotFile", MESSAGES.GotFileEvents());
	map.put("GotText", MESSAGES.GotTextEvents());
	map.put("LongClick", MESSAGES.LongClickEvents());
	map.put("AfterDateSet", MESSAGES.AfterDateSetEvents());
	map.put("TagRead", MESSAGES.TagReadEvents());
	map.put("TagWritten", MESSAGES.TagWrittenEvents());
	map.put("PositionChanged", MESSAGES.PositionChangedEvents());
	map.put("AfterSelecting", MESSAGES.AfterSelectingEvents());
	map.put("AfterTimeSet", MESSAGES.AfterTimeSetEvents());
	map.put("GotTranslation", MESSAGES.GotTranslationEvents());
	map.put("Shaking", MESSAGES.ShakingEvents());

    return map;
  }
}