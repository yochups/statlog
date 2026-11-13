// KubeJS server_scripts example for Minecraft 1.21.1.
const Statlog = Java.loadClass('dev.nidza.statlog.server.StatlogApi')

// Short keys come from key = "strength" in statlog-attributes.toml.
// Full attribute IDs still work.
function strengthLevel(player) {
  return Statlog.statLevel(player, 'strength')
}

PlayerEvents.loggedIn(event => {
  // Grant this player permission to edit when edit_access = "SCRIPT".
  Statlog.allowEdit(event.player, true)
  Statlog.allowRespec(event.player, false)
})

// Call these from your own quest, NPC, item, or location event:
function rewardPlayer(player) {
  Statlog.addXp(player, 25)
  if (strengthLevel(player) >= 5) Statlog.addPoints(player, 'POWER', 2)
  Statlog.allowEdit(player, true)
  Statlog.open(player, 'edit')
}

function lockUpgrades(player) {
  Statlog.allowEdit(player, false)
  Statlog.allowUpgrade(player, 'strength', false)
}
