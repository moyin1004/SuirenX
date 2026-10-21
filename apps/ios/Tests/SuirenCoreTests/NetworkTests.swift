import XCTest
@testable import SuirenCore

private final class DisabledAccountResponse: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "disabled-account.invalid" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let response = HTTPURLResponse(url: request.url!, statusCode: 401, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(#"{"error":"This account is disabled","code":"account_disabled"}"#.utf8))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

private final class OldSaleServerResponse: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "old-sale.invalid" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        XCTAssertEqual(request.url?.path, "/healthz", "must not upload a priced asset to an old server")
        XCTAssertNil(request.value(forHTTPHeaderField: "Authorization"))
        let response = HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(#"{"status":"ok"}"#.utf8)); client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

final class NetworkTests: XCTestCase {
    func testOldServerIsDetectedBeforeUploadingSalePrice() async throws {
        let configuration = URLSessionConfiguration.ephemeral; configuration.protocolClasses = [OldSaleServerResponse.self]
        let client = HTTPClient(configuration: configuration)
        var a = Asset(name: "sale", priceCents: 100, purchaseDate: .today); a.salePriceCents = 0
        var request = SyncRequest(); request.changes = [AssetPayload(a)]
        let credential = Credential(serverURL: "https://old-sale.invalid/", username: "test", token: "synthetic", expiresAt: "")
        do { _ = try await client.exchange(request, credential: credential); XCTFail("old server accepted sale price") }
        catch { XCTAssertTrue(error.localizedDescription.contains("服务器尚不支持卖出价格")) }
    }
    func testDisabledAccountIsNotReportedAsWrongPassword() async throws {
        let configuration = URLSessionConfiguration.ephemeral; configuration.protocolClasses = [DisabledAccountResponse.self]
        let client = HTTPClient(configuration: configuration)
        do {
            _ = try await client.authenticate(serverURL: "https://disabled-account.invalid/", username: "member", password: "legacy")
            XCTFail("Disabled account authenticated")
        } catch {
            XCTAssertEqual(error.localizedDescription, "账号已被停用，请联系管理员")
        }
    }
}

private final class UntrustedErrorResponse: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "untrusted-error.invalid" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let marker = request.url?.deletingLastPathComponent().lastPathComponent ?? ""
        let object: [String: String]
        switch marker {
        case "safe": object = ["code": "temporary_failure", "error": "token=SECRET /private/sensitive.sqlite"]
        case "long": object = ["code": String(repeating: "a", count: 65)]
        case "newline": object = ["code": "temporary_failure\n"]
        case "malicious": object = ["code": "bad code\nSECRET", "error": "raw prose"]
        default: object = ["error": "token=SECRET /private/sensitive.sqlite"]
        }
        let response = HTTPURLResponse(url: request.url!, statusCode: 500, httpVersion: "HTTP/1.1", headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: try! JSONSerialization.data(withJSONObject: object))
        client?.urlProtocolDidFinishLoading(self)
    }
    override func stopLoading() {}
}

extension NetworkTests {
    func testServerErrorProseAndMalformedCodesNeverReachPresentation() async throws {
        let configuration = URLSessionConfiguration.ephemeral; configuration.protocolClasses = [UntrustedErrorResponse.self]
        let client = HTTPClient(configuration: configuration)
        for marker in ["prose", "malicious", "newline", "long", "safe"] {
            do {
                try await client.health(serverURL: "https://untrusted-error.invalid/\(marker)")
                XCTFail("HTTP 500 accepted")
            } catch let error as SuirenError {
                XCTAssertEqual(error.code, .request_failed_http)
                XCTAssertEqual(error.arguments, ["500", marker == "safe" ? " [temporary_failure]" : ""], "Unknown or malformed code must never show remote prose")
                XCTAssertFalse(error.message.contains("SECRET"))
            }
        }
    }
}
