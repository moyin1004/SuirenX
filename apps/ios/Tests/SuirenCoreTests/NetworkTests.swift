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

final class NetworkTests: XCTestCase {
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
