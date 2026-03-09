// Localization completed
angular.module('headwind-kiosk')
    .factory('alertService', function ($uibModal, localization) {

        var showAlert = function (message, callback, okButtonTextKey) {
            var modalInstance = $uibModal.open({
                templateUrl: 'app/shared/view/alert.html',
                controller: 'AlertController',
                resolve: {
                    message: function () {
                        return message;
                    },
                    okButtonTextKey: function () {
                        if (okButtonTextKey) {
                            return okButtonTextKey;
                        } else {
                            return 'button.close';
                        }
                    }
                }
            });

            modalInstance.result.then(function () {
                if (callback) callback();
            }).catch(function () {});

            return modalInstance;
        };

        return {
            showAlertMessage: function (message, callback, okButtonTextKey) {
                return showAlert(message, callback, okButtonTextKey);
            },
            onRequestFailure: function (response) {
                console.error("Error when sending request to server", response);
                return showAlert(localization.localize('error.request.failure'));
            }
        }
    })
    .controller('AlertController', function ($scope, $uibModalInstance, message, okButtonTextKey) {
        $scope.message = message;
        $scope.okButtonTextKey = okButtonTextKey;
        $scope.OK = function () {
            $uibModalInstance.close();
        }
    });
