// Localization completed
angular.module('headwind-kiosk')
    .factory('progressDialog', function ($uibModal) {
        return {
            show: function (message) {
                var modalInstance = $uibModal.open({
                    templateUrl: 'app/shared/view/progress.html',
                    controller: 'ProgressDialogController',
                    resolve: {
                        message: function () {
                            return message;
                        }
                    }
                });

                return modalInstance;
            }
        }
    })
    .controller('ProgressDialogController', function ($scope, $uibModalInstance, message) {
        $scope.message = message;
        $scope.OK = function () {
            $uibModalInstance.close();
        }
    });
