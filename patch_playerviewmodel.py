import re

with open('app/src/main/java/com/example/ui/viewmodels/PlayerViewModel.kt', 'r') as f:
    content = f.read()

# 1. Add uiEvent definition
if 'val uiEvent' not in content:
    content = content.replace('private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())',
                              'private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())\n    private val _uiEvent = kotlinx.coroutines.flow.MutableSharedFlow<String>()\n    val uiEvent = _uiEvent.kotlinx.coroutines.flow.asSharedFlow()')
    content = content.replace('kotlinx.coroutines.flow.asSharedFlow', 'asSharedFlow')
    if 'import kotlinx.coroutines.flow.asSharedFlow' not in content:
        content = content.replace('import kotlinx.coroutines.flow.asStateFlow', 'import kotlinx.coroutines.flow.asStateFlow\nimport kotlinx.coroutines.flow.MutableSharedFlow\nimport kotlinx.coroutines.flow.asSharedFlow')

# 2. Add emit on tracker sync
tracker_old = '''                                        val tracker = com.example.OtakuHubApp.instance.trackerManager.getTracker(track.trackerId)
                                        tracker?.update(updatedTrack)
                                    }'''
tracker_new = '''                                        val tracker = com.example.OtakuHubApp.instance.trackerManager.getTracker(track.trackerId)
                                        tracker?.update(updatedTrack)
                                        tracker?.let {
                                            _uiEvent.emit("Progres tersinkronisasi dengan ${it.name}")
                                        }
                                    }'''
content = content.replace(tracker_old, tracker_new)

with open('app/src/main/java/com/example/ui/viewmodels/PlayerViewModel.kt', 'w') as f:
    f.write(content)
