Feature: HF-YOUTUBE-PUBLISH US-2 — YouTube publication flow

  As a trainer publishing a rendered video
  I want the plugin to upload it to YouTube through the shared VideoPublisher port
  So that a produced video reaches the channel without a manual step

  Scenario: A successful publish returns the video URL
    Given a youtube publish fake transport returning a token and a session and the video id "VID"
    And a youtube publish credentials with a refresh token
    When I publish a youtube video from a temp file
    Then the youtube publish result is published
    And the youtube publish url is "https://youtu.be/VID"

  Scenario: Incomplete credentials degrade without any request
    Given a youtube publish fake transport returning a token and a session and the video id "VID"
    And a youtube publish credentials without a refresh token
    When I publish a youtube video from a temp file
    Then the youtube publish result is degraded
    And the youtube publish transport received no request

  Scenario: A refused token fails
    Given a youtube publish fake transport refusing the token
    And a youtube publish credentials with a refresh token
    When I publish a youtube video from a temp file
    Then the youtube publish result is failed

  Scenario: Disabled publishing degrades
    Given a youtube publish publisher disabled
    And a youtube publish credentials with a refresh token
    When I publish a youtube video from a temp file
    Then the youtube publish result is degraded
