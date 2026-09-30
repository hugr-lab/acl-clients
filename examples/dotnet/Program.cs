// Connect to a duckdb-acl node's Flight SQL door from .NET (Apache.Arrow.Flight.Sql) and read what
// the principal may see. Authenticate with a bearer token (ACL_TOKEN) or a user name and password
// (ACL_USER / ACL_PASSWORD): the door runs the IdP's password grant for you and hands the token back.
//
//   ACL_URI=https://localhost:32800 ACL_TOKEN=$(../../dev/token.sh) dotnet run
//   ACL_URI=https://localhost:32800 ACL_USER=analyst2 ACL_PASSWORD=analyst2-pass dotnet run
//
// ACL_INSECURE=1 skips certificate verification (the dev node's self-signed certificate) - never in
// production.
using System.Net;
using Apache.Arrow;
using Apache.Arrow.Flight;
using Apache.Arrow.Flight.Client;
using Apache.Arrow.Flight.Sql;
using Apache.Arrow.Flight.Sql.Client;
using Grpc.Core;
using Grpc.Net.Client;

var uri = Env("ACL_URI") ?? "https://localhost:32800";
var token = Env("ACL_TOKEN");
var user = Env("ACL_USER");
var password = Env("ACL_PASSWORD");
var sql = Env("ACL_SQL") ?? "SELECT id, tenant, amount FROM orders ORDER BY id";
if (token == null && user == null) {
	Console.Error.WriteLine("set ACL_TOKEN, or ACL_USER and ACL_PASSWORD");
	return 2;
}

var tls = new SocketsHttpHandler();
if (Env("ACL_INSECURE") == "1") {
	tls.SslOptions.RemoteCertificateValidationCallback = (_, _, _, _) => true;
}
// The door keeps one server-side session per client (transactions, session temp tables) by a cookie;
// grpc-dotnet has no cookie support of its own, so a handler carries it.
using var channel = GrpcChannel.ForAddress(uri, new GrpcChannelOptions { HttpHandler = new CookieHandler(tls) });
var flight = new FlightClient(channel);

var bearer = token != null ? "Bearer " + token : await PasswordHandshake(flight, user!, password ?? "");
var options = new FlightCallOptions { Headers = new Metadata { { "authorization", bearer } } };

var client = new FlightSqlClient(flight);
var info = await client.ExecuteAsync(sql, default, options);
foreach (var endpoint in info.Endpoints) {
	await foreach (var batch in client.DoGetAsync(endpoint.Ticket, options)) {
		Print(batch);
	}
}
return 0;

// The Flight password handshake: `authorization: Basic` on Handshake; the door answers the bearer the
// IdP issued in the response headers (or trailers).
static async Task<string> PasswordHandshake(FlightClient flight, string user, string password) {
	var basic = Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes(user + ":" + password));
	using var call = flight.Handshake(new Metadata { { "authorization", "Basic " + basic } });
	await call.RequestStream.CompleteAsync();
	var headers = await call.ResponseHeadersAsync;
	while (await call.ResponseStream.MoveNext(CancellationToken.None)) {
	}
	var found = headers.Concat(call.GetTrailers()).FirstOrDefault(h => h.Key == "authorization");
	return found?.Value ?? throw new InvalidOperationException("the door answered no bearer");
}

static void Print(RecordBatch batch) {
	for (var row = 0; row < batch.Length; row++) {
		var cells = Enumerable.Range(0, batch.ColumnCount).Select(c => Cell(batch.Column(c), row));
		Console.WriteLine(string.Join("\t", cells));
	}
}

static string Cell(IArrowArray column, int row) => column switch {
	_ when column.IsNull(row) => "NULL",
	StringArray s => s.GetString(row),
	Int32Array i => i.GetValue(row).ToString()!,
	Int64Array l => l.GetValue(row).ToString()!,
	DoubleArray d => d.GetValue(row).ToString()!,
	Decimal128Array m => m.GetValue(row).ToString()!,
	_ => $"<{column.Data.DataType.Name}>",
};

static string? Env(string name) => string.IsNullOrEmpty(Environment.GetEnvironmentVariable(name))
	? null : Environment.GetEnvironmentVariable(name);

// Keeps the cookies the door sets and sends them back on every later call.
sealed class CookieHandler(HttpMessageHandler inner) : DelegatingHandler(inner) {
	private readonly CookieContainer jar = new();

	protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancel) {
		var cookies = jar.GetCookieHeader(request.RequestUri!);
		if (cookies.Length > 0) {
			request.Headers.Add("cookie", cookies);
		}
		var response = await base.SendAsync(request, cancel);
		if (response.Headers.TryGetValues("set-cookie", out var set)) {
			foreach (var cookie in set) {
				jar.SetCookies(request.RequestUri!, cookie);
			}
		}
		return response;
	}
}
