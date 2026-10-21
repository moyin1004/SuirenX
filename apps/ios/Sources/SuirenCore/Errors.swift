import Foundation

/// Stable error identities and arguments cross the domain/presentation boundary.
/// The default message preserves existing diagnostic callers; applications localize by code.
public struct SuirenError: LocalizedError, Equatable {
    public let message: String
    public let code: Code?
    public let arguments: [String]
    public let technicalDetail: String?
    public init(_ message: String) { self.message = message; code = nil; arguments = []; technicalDetail = nil }
    public init(_ code: Code, arguments: [String] = [], technicalDetail: String? = nil) {
        self.code = code; self.arguments = arguments; self.technicalDetail = technicalDetail
        message = String(format: code.defaultMessage, arguments: arguments.map { $0 as CVarArg })
    }
    public var errorDescription: String? { message }
    public enum Code: String, CaseIterable, Sendable {
        case unexpected = "error.unexpected"
        case connection_failed_check_the_network_and_server_address_local_data = "ui.connection_failed_check_the_network_and_server_address_local_data"
        case sync_timed_out_the_pending_batch_was_kept = "ui.sync_timed_out_the_pending_batch_was_kept"
        case incorrect_account_or_password = "ui.incorrect_account_or_password"
        case the_health_endpoint_returned_unauthorized_check_the_server_address = "ui.the_health_endpoint_returned_unauthorized_check_the_server_address"
        case invalid_server_asset_date_or_status = "ui.invalid_server_asset_date_or_status"
        case invalid_server_retirement_date = "ui.invalid_server_retirement_date"
        case invalid_server_warranty_date = "ui.invalid_server_warranty_date"
        case invalid_server_supply_date_or_status = "ui.invalid_server_supply_date_or_status"
        case invalid_server_opened_date = "ui.invalid_server_opened_date"
        case could_not_open_the_local_database = "ui.could_not_open_the_local_database"
        case this_database_is_from_a_newer_version_and_cannot_be = "ui.this_database_is_from_a_newer_version_and_cannot_be"
        case could_not_save = "ui.could_not_save"
        case could_not_bind_the_record_id = "ui.could_not_bind_the_record_id"
        case could_not_read = "ui.could_not_read"
        case could_not_read_the_database_version = "ui.could_not_read_the_database_version"
        case local_database_transaction_failed = "ui.local_database_transaction_failed"
        case invalid_server_address = "ui.invalid_server_address"
        case invalid_server_response = "ui.invalid_server_response"
        case this_account_is_disabled_contact_the_administrator = "ui.this_account_is_disabled_contact_the_administrator"
        case the_current_server_cannot_verify_this_account_explicitly_sign_out = "ui.the_current_server_cannot_verify_this_account_explicitly_sign_out"
        case too_many_attempts_try_again_later = "ui.too_many_attempts_try_again_later"
        case new_account_registration_is_closed_contact_the_administrator = "ui.new_account_registration_is_closed_contact_the_administrator"
        case this_account_already_exists_sign_in_or_choose_another_name = "ui.this_account_already_exists_sign_in_or_choose_another_name"
        case the_server_returned_a_redirect_enter_the_final_server_address = "ui.the_server_returned_a_redirect_enter_the_final_server_address"
        case request_failed_http = "ui.request_failed_http"
        case the_health_endpoint_did_not_return_status_ok = "ui.the_health_endpoint_did_not_return_status_ok"
        case the_server_did_not_return_valid_credentials = "ui.the_server_did_not_return_valid_credentials"
        case the_server_does_not_support_sale_prices_yet_upgrade_the = "ui.the_server_does_not_support_sale_prices_yet_upgrade_the"
        case sync_settings_or_account_changed_the_original_batch_was_kept = "ui.sync_settings_or_account_changed_the_original_batch_was_kept"
        case sync_is_already_running = "ui.sync_is_already_running"
        case sign_in_to_a_sync_account_first = "ui.sign_in_to_a_sync_account_first"
        case sync_timed_out_the_batch_was_kept_for_retry = "ui.sync_timed_out_the_batch_was_kept_for_retry"
        case backup_file_is_too_large = "ui.backup_file_is_too_large"
        case unsupported_backup_format_or_version = "ui.unsupported_backup_format_or_version"
        case the_backup_contains_duplicate_or_empty_ids = "ui.the_backup_contains_duplicate_or_empty_ids"
        case restore_archived_assets_first = "ui.restore_archived_assets_first"
        case sale_price_cannot_be_negative = "ui.sale_price_cannot_be_negative"
        case record_not_found = "ui.record_not_found"
        case sale_prices_can_only_be_set_for_archived_assets = "ui.sale_prices_can_only_be_set_for_archived_assets"
        case the_opened_date_cannot_be_after_today_or_the_package = "ui.the_opened_date_cannot_be_after_today_or_the_package"
        case restore_archived_supplies_first = "ui.restore_archived_supplies_first"
        case invalid_settings = "ui.invalid_settings"
        case enable_sync_first = "ui.enable_sync_first"
        case the_sync_target_changed_the_late_response_was_not_applied = "ui.the_sync_target_changed_the_late_response_was_not_applied"
        case the_server_did_not_preserve_sale_prices_upgrade_the_server = "ui.the_server_did_not_preserve_sale_prices_upgrade_the_server"
        case the_server_response_is_missing_acknowledgements_the_original_batch_was = "ui.the_server_response_is_missing_acknowledgements_the_original_batch_was"
        case the_sync_target_changed = "ui.the_sync_target_changed"
        case retry_the_unacknowledged_batch_first = "ui.retry_the_unacknowledged_batch_first"
        case this_conflict_has_already_been_resolved = "ui.this_conflict_has_already_been_resolved"
        case remote_snapshot_unavailable_check_account_ownership = "ui.remote_snapshot_unavailable_check_account_ownership"
        case both_copies_are_deleted_there_is_nothing_to_save = "ui.both_copies_are_deleted_there_is_nothing_to_save"
        case password_8_72_characters_english_letters_digits_ascii_symbols_and_spaces = "account.password_requirements"
        case invalid_date = "ui.invalid_date"
        case check_the_name_amount_and_purchase_date = "ui.check_the_name_amount_and_purchase_date"
        case warranty_end_date_cannot_be_before_the_purchase_date = "ui.warranty_end_date_cannot_be_before_the_purchase_date"
        case retirement_date_must_be_between_the_purchase_date_and_today = "ui.retirement_date_must_be_between_the_purchase_date_and_today"
        case notes_can_contain_up_to_2_000_utf_16_code = "ui.notes_can_contain_up_to_2_000_utf_16_code"
        case unknown_asset_status = "ui.unknown_asset_status"
        case check_the_supply_name_category_location_and_notes = "ui.check_the_supply_name_category_location_and_notes"
        case provide_both_the_opened_date_and_validity_period_the_validity = "ui.provide_both_the_opened_date_and_validity_period_the_validity"
        case invalid_timestamp = "ui.invalid_timestamp"
        case enter_a_nonnegative_amount_with_at_most_two_decimal_places = "ui.enter_a_nonnegative_amount_with_at_most_two_decimal_places"
        case amount_is_too_large = "ui.amount_is_too_large"
        case enter_a_valid_server_address = "ui.enter_a_valid_server_address"
        case invalid_address_release_builds_require_https_without_credentials_query_parameters = "ui.invalid_address_release_builds_require_https_without_credentials_query_parameters"
        case invalid_ipv4_address = "ui.invalid_ipv4_address"
        case account_is_required_and_must_be_at_most_100_utf = "ui.account_is_required_and_must_be_at_most_100_utf"
        case enter_a_password = "ui.enter_a_password"
        case passwords_do_not_match = "ui.passwords_do_not_match"
        public var defaultMessage: String { switch self {
        case .unexpected: return "操作未完成，请重试。若问题持续，请检查数据或服务器设置。"
        case .connection_failed_check_the_network_and_server_address_local_data: return "连接失败，请检查网络和服务器地址；本机数据已保留"
        case .sync_timed_out_the_pending_batch_was_kept: return "同步超时，待发送批次已保留"
        case .incorrect_account_or_password: return "账号或密码不正确"
        case .the_health_endpoint_returned_unauthorized_check_the_server_address: return "健康接口返回未授权，请检查服务器地址"
        case .invalid_server_asset_date_or_status: return "服务器资产日期或状态无效"
        case .invalid_server_retirement_date: return "服务器退役日期无效"
        case .invalid_server_warranty_date: return "服务器保修日期无效"
        case .invalid_server_supply_date_or_status: return "服务器用品日期或状态无效"
        case .invalid_server_opened_date: return "服务器开封日期无效"
        case .could_not_open_the_local_database: return "打开本机数据库失败"
        case .this_database_is_from_a_newer_version_and_cannot_be: return "数据库来自更新版本，无法降级打开"
        case .could_not_save: return "保存 %@ 失败"
        case .could_not_bind_the_record_id: return "绑定记录编号失败"
        case .could_not_read: return "读取 %@ 失败"
        case .could_not_read_the_database_version: return "读取数据库版本失败"
        case .local_database_transaction_failed: return "本机数据库事务失败"
        case .invalid_server_address: return "服务器地址无效"
        case .invalid_server_response: return "服务器响应无效"
        case .this_account_is_disabled_contact_the_administrator: return "账号已被停用，请联系管理员"
        case .the_current_server_cannot_verify_this_account_explicitly_sign_out: return "当前服务器无法验证账号，请先显式退出账号，再重新登录"
        case .too_many_attempts_try_again_later: return "尝试过于频繁，请稍后重试"
        case .new_account_registration_is_closed_contact_the_administrator: return "服务器已关闭新账号注册，请联系管理员"
        case .this_account_already_exists_sign_in_or_choose_another_name: return "账号已存在，请登录或更换账号名"
        case .the_server_returned_a_redirect_enter_the_final_server_address: return "服务器返回重定向，请填写最终服务器地址"
        case .request_failed_http: return "请求失败（HTTP %@）%@"
        case .the_health_endpoint_did_not_return_status_ok: return "健康接口未返回 status=ok"
        case .the_server_did_not_return_valid_credentials: return "服务器未返回有效凭据"
        case .the_server_does_not_support_sale_prices_yet_upgrade_the: return "服务器尚不支持卖出价格，请先升级服务器；本机数据和批次已保留"
        case .sync_settings_or_account_changed_the_original_batch_was_kept: return "同步配置或账号已改变，原批次已保留；请先退出账号后重新登录"
        case .sync_is_already_running: return "同步正在进行"
        case .sign_in_to_a_sync_account_first: return "请先登录同步账号"
        case .sync_timed_out_the_batch_was_kept_for_retry: return "同步超时，批次已保留供重试"
        case .backup_file_is_too_large: return "备份文件过大"
        case .unsupported_backup_format_or_version: return "不支持的备份格式或版本"
        case .the_backup_contains_duplicate_or_empty_ids: return "备份包含重复或空编号"
        case .restore_archived_assets_first: return "归档资产请先恢复"
        case .sale_price_cannot_be_negative: return "卖出价格不能为负数"
        case .record_not_found: return "记录不存在"
        case .sale_prices_can_only_be_set_for_archived_assets: return "仅归档资产可填写卖出价格"
        case .the_opened_date_cannot_be_after_today_or_the_package: return "开封日不能晚于今天或包装到期日"
        case .restore_archived_supplies_first: return "归档用品请先恢复"
        case .invalid_settings: return "设置值无效"
        case .enable_sync_first: return "请先开启同步"
        case .the_sync_target_changed_the_late_response_was_not_applied: return "同步目标已改变，未应用迟到响应"
        case .the_server_did_not_preserve_sale_prices_upgrade_the_server: return "服务器未保留卖出价格，请先升级服务器；本机数据和批次已保留"
        case .the_server_response_is_missing_acknowledgements_the_original_batch_was: return "服务器响应缺少确认，原批次已保留"
        case .the_sync_target_changed: return "同步目标已改变"
        case .retry_the_unacknowledged_batch_first: return "请先重试未确认批次"
        case .this_conflict_has_already_been_resolved: return "该冲突已处理"
        case .remote_snapshot_unavailable_check_account_ownership: return "另一端快照不可用，请检查账号归属"
        case .both_copies_are_deleted_there_is_nothing_to_save: return "双方均已删除，没有可另存内容"
        case .password_8_72_characters_english_letters_digits_ascii_symbols_and_spaces: return "8–72 个字符，仅限英文字母、数字、半角符号和空格；不支持中文或换行，无大小写、数字组合要求。"
        case .invalid_date: return "无效日期：%@"
        case .check_the_name_amount_and_purchase_date: return "请检查名称、金额和购买日期"
        case .warranty_end_date_cannot_be_before_the_purchase_date: return "保修截止日不能早于购买日期"
        case .retirement_date_must_be_between_the_purchase_date_and_today: return "退役日期必须在购买日至今天之间"
        case .notes_can_contain_up_to_2_000_utf_16_code: return "备注最多 2000 字，标签最多 20 个且每个最多 30 字"
        case .unknown_asset_status: return "未知资产状态"
        case .check_the_supply_name_category_location_and_notes: return "请检查用品名称、分类、位置和备注"
        case .provide_both_the_opened_date_and_validity_period_the_validity: return "开封日期与有效天数须同时填写，有效天数须为正整数"
        case .invalid_timestamp: return "时间戳无效"
        case .enter_a_nonnegative_amount_with_at_most_two_decimal_places: return "金额须为非负数字，最多两位小数"
        case .amount_is_too_large: return "金额过大"
        case .enter_a_valid_server_address: return "请输入有效服务器地址"
        case .invalid_address_release_builds_require_https_without_credentials_query_parameters: return "地址无效；正式版本须使用 HTTPS，不能含凭据、查询参数或片段"
        case .invalid_ipv4_address: return "IPv4 地址无效"
        case .account_is_required_and_must_be_at_most_100_utf: return "账号不能为空且最多 100 个 UTF-8 字节"
        case .enter_a_password: return "请输入密码"
        case .passwords_do_not_match: return "两次密码不一致"
        } }
    }
}


/// Only structured, recognized errors provide UI arguments. Unknown platform, decoding,
/// or database errors retain their original cause without exposing paths or credentials.
public struct ErrorPresentation {
    public let code: SuirenError.Code
    public let arguments: [String]
    public let cause: Error
    public init(_ error: Error) {
        cause = error
        if let known = error as? SuirenError, let code = known.code {
            self.code = code; arguments = known.arguments
        } else { code = .unexpected; arguments = [] }
    }
}
