// Terminal plugin — AngularJS module
//
// Registers state `plugin-terminal` under the Functions menu and exposes
// `terminalPluginService` for the controller to call the REST backend.

angular.module('plugin-terminal', ['ngResource', 'ui.bootstrap', 'ui.router', 'ncy-angular-breadcrumb'])

    .config(function ($stateProvider) {
        try {
            $stateProvider.state('plugin-terminal', {
                url: '/' + 'plugin-terminal',
                templateUrl: 'app/components/main/view/content.html',
                controller: 'TabController',
                ncyBreadcrumb: {
                    label: '{{"breadcrumb.plugin.terminal.main" | localize}}'
                },
                resolve: {
                    openTab: function () { return 'plugin-terminal'; }
                }
            });
        } catch (e) {
            console.log('An error when adding state plugin-terminal', e);
        }
    })

    .factory('terminalPluginService', function ($resource) {
        return $resource('', {}, {
            // Devices for picker
            listDevices: {
                url: 'rest/plugins/terminal/private/devices',
                method: 'GET'
            },
            // Sessions
            openSession: {
                url: 'rest/plugins/terminal/private/sessions',
                method: 'POST'
            },
            closeSession: {
                url: 'rest/plugins/terminal/private/sessions/:id/close',
                method: 'POST',
                params: { id: '@id' }
            },
            // Exec
            exec: {
                url: 'rest/plugins/terminal/private/exec',
                method: 'POST'
            },
            // Output poll
            output: {
                url: 'rest/plugins/terminal/private/output',
                method: 'GET',
                isArray: false
            },
            // Snippets
            listSnippets: {
                url: 'rest/plugins/terminal/private/snippets',
                method: 'GET'
            },
            createSnippet: {
                url: 'rest/plugins/terminal/private/snippets',
                method: 'POST'
            },
            updateSnippet: {
                url: 'rest/plugins/terminal/private/snippets/:id',
                method: 'PUT',
                params: { id: '@id' }
            },
            deleteSnippet: {
                url: 'rest/plugins/terminal/private/snippets/:id',
                method: 'DELETE',
                params: { id: '@id' }
            },
            // Favorites
            listFavorites: {
                url: 'rest/plugins/terminal/private/favorites',
                method: 'GET'
            },
            favorite: {
                url: 'rest/plugins/terminal/private/favorites/:snippetId',
                method: 'POST',
                params: { snippetId: '@snippetId' }
            },
            unfavorite: {
                url: 'rest/plugins/terminal/private/favorites/:snippetId',
                method: 'DELETE',
                params: { snippetId: '@snippetId' }
            },
            // Status batch
            status: {
                url: 'rest/plugins/terminal/private/status',
                method: 'GET'
            },
            // History
            history: {
                url: 'rest/plugins/terminal/private/history',
                method: 'GET'
            },
            purge: {
                url: 'rest/plugins/terminal/private/purge/:days',
                method: 'DELETE',
                params: { days: '@days' }
            }
        });
    })

    .controller('PluginTerminalTabController', [
        '$scope', '$rootScope', '$interval', '$timeout', 'terminalPluginService', 'localization',
        function ($scope, $rootScope, $interval, $timeout, terminalPluginService, localization) {

            // ─── State ────────────────────────────────────────────────
            $scope.devices = [];
            $scope.snippets = [];
            $scope.selectedDevices = {};                // id -> bool
            $scope.snippetCategories = [];
            $scope.snippetFilter = '';
            $scope.currentCommand = '';
            $scope.outputs = [];                        // flat list of TerminalOutputEntry
            $scope.session = null;
            $scope.sending = false;
            $scope.history = [];                        // last N commands typed
            $scope.historyIdx = -1;
            $scope.stats = { sent: 0, ok: 0, pending: 0, error: 0 };
            $scope.lastFetchMs = 0;
            $scope.pollingActive = false;
            // Tabs
            $scope.activeTabDeviceId = null;            // null = ALL
            $scope.tabBadges = {};                      // deviceId -> count newest unread
            // Filters
            $scope.grepFilter = '';
            $scope.autoScroll = true;
            // Latency tracking
            $scope.pendingByMsgId = {};                 // messageId -> { sentAt, deviceId, command }
            // Favorites
            $scope.favoriteIds = {};                    // snippetId -> true
            // Snippet editor
            $scope.editorSnippet = null;                // null = closed
            $scope.editorMode = 'create';
            // Destructive confirm
            $scope.confirmDialog = null;                // { snippet, devices, typed, expected, onConfirm }
            // History
            $scope.historyVisible = false;
            $scope.historyItems = [];
            $scope.historyFilter = { deviceId: null, search: '', scope: 'self', days: 7 };
            // Destructive auto-detect
            $scope.dryRun = false;
            $scope.destructivePatterns = [
                /\bpm\s+uninstall\b/i,
                /\bpm\s+clear\b/i,
                /\bam\s+force-stop\b/i,
                /\bsvc\s+power\s+reboot\b/i,
                /\breboot\b/i,
                /\bdpm\s+(set|remove)-device-owner\b/i,
                /\bsettings\s+(put|delete)\b/i,
                /\bsetprop\b/i,
                /\bkill(all)?\b/i,
                /\brm\s+-rf\b/i,
                /\bfactory(reset)?\b/i,
                /\bwipe\b/i,
                /\bsm\s+format\b/i
            ];

            // ─── Boot ─────────────────────────────────────────────────
            $scope.init = function () {
                $scope.refreshDevices();
                $scope.refreshSnippets();
                $scope.refreshFavorites();
                $scope.openSession();
                $scope.history = JSON.parse(localStorage.getItem('plugin.terminal.history') || '[]');
                $scope.lastFetchMs = Date.now();
                $scope.startPolling();
            };

            // ─── Favorites ────────────────────────────────────────────
            $scope.refreshFavorites = function () {
                terminalPluginService.listFavorites({}, function (resp) {
                    if (resp.status === 'OK') {
                        $scope.favoriteIds = {};
                        (resp.data || []).forEach(function (id) { $scope.favoriteIds[id] = true; });
                    }
                });
            };

            $scope.toggleFavorite = function (snippet, $event) {
                if ($event) $event.stopPropagation();
                if ($scope.favoriteIds[snippet.id]) {
                    terminalPluginService.unfavorite({ snippetId: snippet.id }, function (resp) {
                        if (resp.status === 'OK') delete $scope.favoriteIds[snippet.id];
                    });
                } else {
                    terminalPluginService.favorite({ snippetId: snippet.id }, function (resp) {
                        if (resp.status === 'OK') $scope.favoriteIds[snippet.id] = true;
                    });
                }
            };

            $scope.favoriteSnippets = function () {
                return $scope.snippets.filter(function (s) { return $scope.favoriteIds[s.id]; });
            };

            // ─── Snippet editor ───────────────────────────────────────
            $scope.openSnippetEditor = function (snippet, $event) {
                if ($event) $event.stopPropagation();
                if (snippet) {
                    $scope.editorMode = 'edit';
                    $scope.editorSnippet = angular.copy(snippet);
                } else {
                    $scope.editorMode = 'create';
                    $scope.editorSnippet = {
                        category: 'custom',
                        label: '',
                        commands: '',
                        messageType: 'runCommand',
                        destructive: false,
                        sortOrder: 100
                    };
                }
            };

            $scope.closeSnippetEditor = function () { $scope.editorSnippet = null; };

            $scope.saveSnippet = function () {
                var s = $scope.editorSnippet;
                if (!s || !s.label || !s.commands) return;
                if ($scope.editorMode === 'edit' && s.id) {
                    terminalPluginService.updateSnippet({ id: s.id }, s, function (resp) {
                        if (resp.status === 'OK') {
                            $scope.refreshSnippets();
                            $scope.closeSnippetEditor();
                        }
                    });
                } else {
                    terminalPluginService.createSnippet({}, s, function (resp) {
                        if (resp.status === 'OK') {
                            $scope.refreshSnippets();
                            $scope.closeSnippetEditor();
                        }
                    });
                }
            };

            $scope.deleteSnippet = function (snippet, $event) {
                if ($event) $event.stopPropagation();
                if (!snippet.id) return;
                if (snippet.customerId === null || snippet.customerId === undefined) {
                    alert('Não é possível deletar snippet padrão do sistema.');
                    return;
                }
                if (!confirm('Deletar snippet "' + snippet.label + '"?')) return;
                terminalPluginService.deleteSnippet({ id: snippet.id }, function (resp) {
                    if (resp.status === 'OK') $scope.refreshSnippets();
                });
            };

            // ─── Destructive confirmation ─────────────────────────────
            $scope.applySnippetWithGuard = function (snippet) {
                if (!snippet.destructive) {
                    $scope.applySnippet(snippet);
                    return;
                }
                var ids = $scope.selectedDeviceIds();
                if (ids.length === 0) {
                    $scope.applySnippet(snippet);
                    return;
                }
                var firstDev = $scope.devices.filter(function (d) { return d.id === ids[0]; })[0];
                $scope.confirmDialog = {
                    snippet: snippet,
                    devicesCount: ids.length,
                    expected: firstDev ? firstDev.number : '',
                    typed: '',
                    onConfirm: function () {
                        $scope.applySnippet(snippet);
                        $scope.confirmDialog = null;
                    }
                };
            };

            $scope.cancelConfirm = function () { $scope.confirmDialog = null; };

            // ─── History ──────────────────────────────────────────────
            $scope.openHistory = function () {
                $scope.historyVisible = true;
                $scope.refreshHistory();
            };

            $scope.closeHistory = function () { $scope.historyVisible = false; };

            $scope.refreshHistory = function () {
                var since = Date.now() - ($scope.historyFilter.days * 86400000);
                terminalPluginService.history({
                    since: since,
                    deviceId: $scope.historyFilter.deviceId || undefined,
                    search: $scope.historyFilter.search || undefined,
                    scope: $scope.historyFilter.scope,
                    limit: 300
                }, function (resp) {
                    if (resp.status === 'OK') $scope.historyItems = resp.data || [];
                });
            };

            $scope.replay = function (item) {
                $scope.currentCommand = item.command;
                if (item.deviceId) {
                    $scope.selectedDevices = {};
                    $scope.selectedDevices[item.deviceId] = true;
                }
                $scope.historyVisible = false;
            };

            $scope.purgeHistory = function () {
                var days = prompt('Apagar comandos com mais de quantos dias?', '30');
                if (!days) return;
                var n = parseInt(days, 10);
                if (isNaN(n) || n < 1) return;
                if (!confirm('Apagar TODOS comandos com mais de ' + n + ' dias? Não pode desfazer.')) return;
                terminalPluginService.purge({ days: n }, function (resp) {
                    if (resp.status === 'OK') {
                        alert(resp.message || 'OK');
                        $scope.refreshHistory();
                    } else {
                        alert('Falha: ' + (resp.message || 'unknown'));
                    }
                });
            };

            // ─── Devices ──────────────────────────────────────────────
            $scope.refreshDevices = function () {
                terminalPluginService.listDevices({}, function (resp) {
                    if (resp.status === 'OK') $scope.devices = resp.data || [];
                });
            };

            $scope.selectedDeviceIds = function () {
                return Object.keys($scope.selectedDevices)
                    .filter(function (k) { return $scope.selectedDevices[k]; })
                    .map(function (k) { return parseInt(k, 10); });
            };

            $scope.selectAll = function () {
                $scope.devices.forEach(function (d) { $scope.selectedDevices[d.id] = true; });
            };

            $scope.selectNone = function () {
                $scope.devices.forEach(function (d) { $scope.selectedDevices[d.id] = false; });
            };

            $scope.selectOnline = function () {
                $scope.devices.forEach(function (d) { $scope.selectedDevices[d.id] = !!d.online; });
            };

            // ─── Snippets ─────────────────────────────────────────────
            $scope.refreshSnippets = function () {
                terminalPluginService.listSnippets({}, function (resp) {
                    if (resp.status === 'OK') {
                        $scope.snippets = resp.data || [];
                        var cats = {};
                        $scope.snippets.forEach(function (s) {
                            cats[s.category] = cats[s.category] || [];
                            cats[s.category].push(s);
                        });
                        $scope.snippetCategories = Object.keys(cats).sort().map(function (k) {
                            return { name: k, items: cats[k] };
                        });
                    }
                });
            };

            $scope.applySnippet = function (snippet) {
                if (snippet.messageType === 'grantPermissions') {
                    $scope.currentCommand = '@grantPermissions ' + snippet.commands.trim();
                } else {
                    $scope.currentCommand = snippet.commands;
                }
            };

            $scope.filteredSnippets = function (items) {
                var f = ($scope.snippetFilter || '').toLowerCase();
                if (!f) return items;
                return items.filter(function (s) {
                    return s.label.toLowerCase().indexOf(f) >= 0 ||
                           (s.commands || '').toLowerCase().indexOf(f) >= 0;
                });
            };

            // ─── Session ──────────────────────────────────────────────
            $scope.openSession = function () {
                terminalPluginService.openSession({ label: 'Terminal ' + new Date().toLocaleString() },
                    function (resp) {
                        if (resp.status === 'OK') $scope.session = resp.data;
                    });
            };

            // ─── Detect destructive ───────────────────────────────────
            $scope.isDestructive = function (text) {
                if (!text) return false;
                for (var i = 0; i < $scope.destructivePatterns.length; i++) {
                    if ($scope.destructivePatterns[i].test(text)) return true;
                }
                return false;
            };

            $scope.currentIsDestructive = function () {
                return $scope.isDestructive($scope.currentCommand);
            };

            // ─── Send ─────────────────────────────────────────────────
            $scope.send = function (forced) {
                var raw = ($scope.currentCommand || '').trim();
                if (!raw) return;
                var ids = $scope.selectedDeviceIds();
                if (ids.length === 0) {
                    $scope.appendLocal('error', 'Nenhum device selecionado.');
                    return;
                }

                var messageType = 'runCommand';
                var commands;
                if (raw.indexOf('@grantPermissions ') === 0) {
                    messageType = 'grantPermissions';
                    commands = [ raw.substring('@grantPermissions '.length).trim() ];
                } else {
                    commands = raw.split('\n').map(function (l) { return l.trim(); }).filter(Boolean);
                }

                var destructive = $scope.isDestructive(raw);

                // Auto-guard: open confirm modal if destructive & not forced
                if (destructive && !forced) {
                    var firstDev = $scope.devices.filter(function (d) { return d.id === ids[0]; })[0];
                    $scope.confirmDialog = {
                        snippet: { label: 'Comando livre (destrutivo)', commands: raw },
                        devicesCount: ids.length,
                        expected: firstDev ? firstDev.number : '',
                        typed: '',
                        onConfirm: function () {
                            $scope.confirmDialog = null;
                            $scope.send(true);     // recurse with forced=true
                        }
                    };
                    return;
                }

                $scope.sending = true;
                var req = {
                    scope: 'devices',
                    deviceIds: ids,
                    commands: commands,
                    messageType: messageType,
                    destructive: destructive,
                    dryRun: $scope.dryRun,
                    sessionId: $scope.session ? $scope.session.id : null
                };
                terminalPluginService.exec(req, function (resp) {
                    $scope.sending = false;
                    if (resp.status === 'OK') {
                        if (resp.data.dryRun) {
                            $scope.appendLocal('info',
                                '🧪 DRY-RUN: ' + commands.length + ' cmd × ' + ids.length +
                                ' device = ' + (commands.length * ids.length) + ' pushes (NÃO enviados)');
                            $scope.currentCommand = '';
                            return;
                        }
                        $scope.stats.sent += resp.data.totalEnqueued;
                        $scope.stats.pending += resp.data.totalEnqueued;
                        var dc = resp.data.deviceCommands || {};
                        Object.keys(dc).forEach(function (devId) {
                            (dc[devId] || []).forEach(function (msgId, idx) {
                                $scope.pendingByMsgId[msgId] = {
                                    sentAt: Date.now(),
                                    deviceId: parseInt(devId, 10),
                                    command: commands[idx] || ''
                                };
                            });
                        });
                        $scope.appendLocal('info',
                            '$ ' + raw.replace(/\n/g, ' ⏎ ') +
                            '  → ' + ids.length + ' device(s) · ' + resp.data.totalEnqueued + ' push(es)');
                        $scope.pushHistory(raw);
                        $scope.currentCommand = '';
                    } else {
                        $scope.appendLocal('error', 'Falha: ' + (resp.message || 'unknown'));
                    }
                }, function (err) {
                    $scope.sending = false;
                    $scope.appendLocal('error', 'HTTP ' + (err.status || '?') + ' ao enviar.');
                });
            };

            $scope.pushHistory = function (cmd) {
                $scope.history.unshift(cmd);
                if ($scope.history.length > 200) $scope.history.length = 200;
                localStorage.setItem('plugin.terminal.history', JSON.stringify($scope.history));
                $scope.historyIdx = -1;
            };

            $scope.navigateHistory = function (dir) {
                if ($scope.history.length === 0) return;
                $scope.historyIdx = Math.max(-1, Math.min($scope.history.length - 1,
                                                          $scope.historyIdx + dir));
                $scope.currentCommand = $scope.historyIdx === -1 ? '' : $scope.history[$scope.historyIdx];
            };

            // ─── Output polling (adaptive) ────────────────────────────
            $scope.pollingIntervalMs = function () {
                // 1s if any pending, 3s normal, 8s idle (no selected devices)
                if ($scope.stats.pending > 0) return 1000;
                if ($scope.selectedDeviceIds().length === 0) return 8000;
                return 3000;
            };

            $scope.startPolling = function () {
                if ($scope.pollingActive) return;
                $scope.pollingActive = true;
                var tick = function () {
                    if (!$scope.pollingActive) return;
                    var ids = $scope.selectedDeviceIds();
                    var since = $scope.lastFetchMs;
                    terminalPluginService.output({
                        since: since,
                        deviceIds: ids.length ? ids.join(',') : ''
                    }, function (resp) {
                        if (resp.status === 'OK' && resp.data && resp.data.length) {
                            for (var i = 0; i < resp.data.length; i++) {
                                var entry = resp.data[i];
                                entry._displayMessage = $scope.parseEntry(entry);
                                $scope.outputs.push(entry);
                                if (entry.kind === 'exec_result') {
                                    $scope.stats.ok++;
                                    $scope.stats.pending = Math.max(0, $scope.stats.pending - 1);
                                } else if (entry.kind === 'error') {
                                    $scope.stats.error++;
                                }
                                if (entry.deviceId && $scope.activeTabDeviceId !== null &&
                                    entry.deviceId !== $scope.activeTabDeviceId) {
                                    $scope.tabBadges[entry.deviceId] = ($scope.tabBadges[entry.deviceId] || 0) + 1;
                                }
                                if (entry.ts > $scope.lastFetchMs) {
                                    $scope.lastFetchMs = entry.ts;
                                }
                            }
                            if ($scope.outputs.length > 2000) {
                                $scope.outputs = $scope.outputs.slice(-2000);
                            }
                            $timeout(function () {
                                if (!$scope.autoScroll) return;
                                var el = document.getElementById('terminal-console');
                                if (el) el.scrollTop = el.scrollHeight;
                            }, 50);
                        }
                        $timeout(tick, $scope.pollingIntervalMs());
                    }, function () {
                        $timeout(tick, 5000);
                    });
                };
                tick();
            };

            // ─── Latency calculator ───────────────────────────────────
            $scope.entryLatency = function (entry) {
                if (entry.kind !== 'exec_result') return null;
                var pending = $scope.pendingByMsgId[entry.messageId];
                if (!pending) return null;
                var ms = entry.ts - pending.sentAt;
                if (ms < 0) return null;
                if (ms < 1000) return ms + 'ms';
                return (ms / 1000).toFixed(1) + 's';
            };

            $scope.stopPolling = function () { $scope.pollingActive = false; };

            // ─── Console helpers ──────────────────────────────────────
            $scope.appendLocal = function (severity, msg) {
                $scope.outputs.push({
                    id: 'local-' + Date.now(),
                    deviceId: null,
                    deviceNumber: '*',
                    ts: Date.now(),
                    severity: severity === 'error' ? 'ERROR' : 'INFO',
                    message: msg,
                    kind: severity === 'error' ? 'error' : 'local'
                });
            };

            $scope.clearConsole = function () { $scope.outputs = []; };

            $scope.exportConsole = function () {
                var lines = $scope.outputs.map(function (o) {
                    return new Date(o.ts).toISOString() + ' [' + (o.deviceNumber || '?') + '] [' +
                           o.severity + '] ' + o.message;
                });
                var blob = new Blob([lines.join('\n')], { type: 'text/plain' });
                var a = document.createElement('a');
                a.href = URL.createObjectURL(blob);
                a.download = 'terminal-' + Date.now() + '.txt';
                a.click();
            };

            $scope.statusClass = function (entry) {
                switch (entry.kind) {
                    case 'exec_result':   return 'term-ok';
                    case 'push_received': return 'term-pending';
                    case 'error':         return 'term-error';
                    case 'app_op':        return 'term-info';
                    case 'local':         return 'term-local';
                    default:              return 'term-log';
                }
            };

            // ─── Tabs ─────────────────────────────────────────────────
            $scope.selectTab = function (deviceId) {
                $scope.activeTabDeviceId = deviceId;
                if (deviceId !== null) $scope.tabBadges[deviceId] = 0;
            };

            $scope.openTabs = function () {
                var ids = $scope.selectedDeviceIds();
                return ids.map(function (id) {
                    var d = $scope.devices.filter(function (x) { return x.id === id; })[0];
                    return { id: id, label: d ? d.number : ('#' + id), badge: $scope.tabBadges[id] || 0 };
                });
            };

            // ─── Filter / display ─────────────────────────────────────
            $scope.filteredOutputs = function () {
                var f = ($scope.grepFilter || '').toLowerCase();
                var active = $scope.activeTabDeviceId;
                return $scope.outputs.filter(function (o) {
                    if (active !== null && o.deviceId !== active && o.deviceId !== null) return false;
                    if (!f) return true;
                    var hay = ((o._displayMessage || o.message || '') + ' ' + (o.deviceNumber || '')).toLowerCase();
                    return hay.indexOf(f) >= 0;
                });
            };

            // ─── Auto-scroll lock ─────────────────────────────────────
            $scope.onConsoleScroll = function () {
                var el = document.getElementById('terminal-console');
                if (!el) return;
                var atBottom = (el.scrollTop + el.clientHeight) >= (el.scrollHeight - 24);
                $scope.autoScroll = atBottom;
                $scope.$applyAsync();
            };

            // ─── Parse entry: extract latency, command, output ────────
            $scope.parseEntry = function (entry) {
                var msg = entry.message || '';
                // "Executed a command: <cmd> Result: <output>"
                var execMatch = msg.match(/^Executed a command:\s*([\s\S]*?)\s+Result:\s*([\s\S]*)$/);
                if (execMatch) {
                    var cmd = execMatch[1].trim();
                    var out = execMatch[2].trim();
                    return '✅ ' + cmd + '\n   ' + (out || '(empty stdout)').replace(/\n/g, '\n   ');
                }
                // "Got Push Message, type runCommand"
                if (msg.indexOf('Got Push Message') === 0) return '📥 ' + msg;
                // "Silently install ... / Silently uninstall ..."
                if (msg.indexOf('Silently install') === 0) return '📦 ' + msg;
                if (msg.indexOf('Silently uninstall') === 0) return '🗑️ ' + msg;
                if (entry.severity === 'ERROR') return '❌ ' + msg;
                return msg;
            };

            // Auto-init
            $scope.init();

            // Cleanup
            $scope.$on('$destroy', function () {
                $scope.stopPolling();
                if ($scope.session && $scope.session.id) {
                    terminalPluginService.closeSession({ id: $scope.session.id }, {});
                }
            });
        }
    ])
    .run(function (localization) {
        localization.loadPluginResourceBundles("terminal");
    });
