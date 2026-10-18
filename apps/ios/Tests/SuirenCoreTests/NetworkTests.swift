import XCTest
@testable import SuirenCore

private final class DisabledAccountResponse: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { request.url?.host == "disabled-account.invalid" }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }
    override func startLoading() {
        let response = HTTPURLResponse(url: request.url!, statusCode: 401, httpVersion: "HTTP/1.1", headerFields: ["Content-Type": "application/json"])!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: Data(#"{"error":"account_disabled"}"#.utf8))
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
