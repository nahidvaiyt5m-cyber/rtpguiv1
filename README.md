# RtpGui (Paper 26.1.2 / Java 25)

Pop-up 3D RTP selector. Opens only for the player who runs `/rtp`.
Works with BetterRTP by running its command as the player.

## Build
    mvn clean package
Output: `target/RtpGui.jar` -> put in `plugins/`.

## Commands
- `/rtpgui` or bare `/rtp` -> open the GUI
- `/rtpgui toggle <overworld|nether|end> [on|off]`
- `/rtpgui reload`, `/rtpgui status`

## Resource pack (item models, MC 1.21.4+ style)
Each image uses an item model id from config (`model:`):

    assets/rtpgui/items/overworld.json
      {"model":{"type":"minecraft:model","model":"rtpgui:item/overworld"}}
    assets/rtpgui/models/item/overworld.json
      {"parent":"minecraft:item/generated","textures":{"layer0":"rtpgui:item/overworld"}}
    assets/rtpgui/textures/item/overworld.png

Same for `nether`, `end`, `close`.
